package com.yidianhulian.framework;

import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;

import com.yidianhulian.framework.Api.NetworkException;
import com.yidianhulian.framework.db.KVHandler;

/**
 * 异步调用网络api，处理返回的json，
 * <b><font color="red">由于是异步处理，所以该对象不能反复使用,每次请求都必须new一个新对象；</font><b><br>
 * 网络请求考虑了缓存：如果网络不可用时将使用缓存的数据；对于一些复杂的情况，比如多次请求的结果作为一个整体进行缓存；post结果的缓存处理等
 * 也给予支持；总之对于上层调用，尽量做到透明。
 * 
 * @author leeboo
 *
 */
public class CallApiTask extends AsyncTask<Object, Object, JSONObject> {
    public static final String CACHE_DB = "ydhlcache";
    private int mWhat;
    private CallApiListener mListener;
    private Object[] mParams;
    private CacheType mCacheType;
    private FetchType mFetchType;
    private Context mContext;
    private KVHandler mDb;
    private boolean needHandleResultOnPostExecute = true;


    public static void doCallApi(int what, CallApiListener listener, Context context,
            CacheType cacheType, FetchType fetchType, Object... params) {
        new CallApiTask(what, listener, context, cacheType, fetchType, params).execute(params);
    }
    /**
     * CacheType未ignore，fetchtype为disable_cache
     * 
     * @param what
     * @param listener
     * @param context
     * @param params
     */
    public static void doCallApi(int what, CallApiListener listener, Context context, Object... params) {
        new CallApiTask(what, listener, context, CacheType.IGNORE, FetchType.FETCH_API, params).execute(params);
    }
    public CallApiTask(int what, CallApiListener listener, Context context, CacheType cacheType,FetchType fetchType,
            Object... params) {
        this.mWhat      = what;
        this.mListener  = listener;
        this.mParams    = params;
        this.mCacheType = cacheType == null ? CacheType.IGNORE : cacheType;
        this.mFetchType = fetchType == null ? FetchType.FETCH_API : fetchType;
        this.mContext   = context;
        
        mDb = new KVHandler(mContext, CACHE_DB, null, 1);
    }
    public CallApiTask(int what, CallApiListener listener, Context context, Object... params) {
        this(what, listener, context, CacheType.IGNORE, FetchType.FETCH_API, params);
    }

    @Override
    protected void onPreExecute() {
        if (mFetchType == FetchType.FETCH_CACHE_THEN_API || mFetchType==FetchType.FETCH_CACHE_AWAYS_API){
            JSONObject cache = getCache(mListener.getCacheKey(mWhat, mCacheType, mParams));
            if(mFetchType == FetchType.FETCH_CACHE_THEN_API){
                mListener.handleResult(mWhat, cache, false, mParams);
                return;
            }
            
            if((mFetchType == FetchType.FETCH_CACHE_AWAYS_API && cache!=null)){ 
                mListener.handleResult(mWhat, cache, true, mParams);
                needHandleResultOnPostExecute = false;
            }
        }
        
    }

    @Override
    protected void onProgressUpdate(Object... values) {
        mListener.apiNetworkException((Exception)values[0]);
    }
    @Override
    protected JSONObject doInBackground(Object... params) {
        
        if(mFetchType==FetchType.FETCH_CACHE || mFetchType==FetchType.FETCH_CACHE_ELSE_API ){
            JSONObject cache = getCache(mListener.getCacheKey(mWhat, mCacheType, mParams));
            if(mFetchType==FetchType.FETCH_CACHE)return cache;
            
            if(cache != null) return cache;
        }
        try{
            Api api = mListener.getApi(mWhat, params);
            if(api==null)return null;
            JSONObject result = api.invoke();
            //调用成功，并且可以缓存
            if(mListener.isCallApiSuccess(result) && mCacheType != CacheType.IGNORE){
                saveCache(mListener.getCacheKey(mWhat, mCacheType, mParams), result, mCacheType);
            }
            return result;
        }catch(NetworkException e){
            if(mFetchType==FetchType.FETCH_API_ELSE_CACHE){
                return getCache(mListener.getCacheKey(mWhat, mCacheType, mParams));
            }
            this.publishProgress(e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(JSONObject result) {
        this.cancel(true);
        mDb.close();
        if( ! needHandleResultOnPostExecute){
            return;
        }
        
        mListener.handleResult(mWhat, result, true, mParams);
        
    }

    public JSONObject getCache(String key) {
        if(key==null || "".equals(key.trim()))return null;
        try {
            String value = mDb.getValue(key);
            if(value==null) return null;
            return new JSONObject(value);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveCache(String key, JSONObject result, CacheType cacheType) {
        if(key==null || "".equals(key.trim()) || result==null)return;
        JSONObject cache = getCache(key);
        try{
            switch(cacheType){
            case IGNORE: return;
//            case CUSTOM: 
//                if(cache==null){
//                    mDb.setValue(key, result.toString());
//                }else{
//                    mDb.setValue(key, mListener.handleCache(mWhat, result, cache).toString());
//                };return;
            case APPEND:    
                if(cache==null){
                    mDb.setValue(key, result.toString());
                }else{
                    mDb.setValue(key, mListener.appendResult(mWhat, result, cache).toString());
                }
                return;
            case PREPEND:   
                if(cache==null){
                    mDb.setValue(key, result.toString());
                }else{
                    mDb.setValue(key, mListener.prependResult(mWhat, result, cache).toString());
                }
                return;
            case REPLACE:   mDb.setValue(key, result.toString());return;
            }
        }catch(Exception e){
            e.printStackTrace();
            //leebo @10/26 忽略数据合并异常
        }
    }
    
    /**
     * 缓存如何存储
     * 
     * @author leeboo
     *
     */
    public enum CacheType {
//        /**
//         * 通过handleCache自定义处理缓存，
//         */
//        CUSTOM,
        /**
         * 不缓存
         */
        IGNORE,
        /**
         * 整体替换
         */
        REPLACE,
        /**
         * 追加在结尾
         */
        APPEND,
        /**
         * 追加在开头
         */
        PREPEND
    }
    
    /**
     * api调用策略
     * @author leeboo
     *
     */
    public enum FetchType{
        /**
         * 先取缓存的数据，然后在调用api, 会调用handleresult两次
         */
        FETCH_CACHE_THEN_API,
        /**
         * 先调用api，如果调用失败则取缓存，会调用handleresult一次
         */
        FETCH_API_ELSE_CACHE,
        
        /**
         * 直接通过api获取数据,不管api调用成功与否，不通过缓存
         */
        FETCH_API,
        /**
         * 直接通过cache获取，不调用api
         */
        FETCH_CACHE,
        /**
         * 如果cache有，直接通过cache获取，不调用api；如果cache没有，调用api
         */
        FETCH_CACHE_ELSE_API,
        /**
         * 总是会调用API；如果cache有，直接通过cache获取，回调handleResult；api的调用结果不回调handleResult；如果cache没有
         * 则在api调用后回调handleResult
         */
        FETCH_CACHE_AWAYS_API,
    }

    public interface CallApiListener {

        /**
         * 调用接口，返回Api对象，该方法会在Task线程中调用，<b><font
         * color="red">所以不要在该方法中操作UI上的东西</font></b> what
         * 一个activity调用多个api时what用于区分是那个api
         * 
         * @return
         */
        public abstract Api getApi(int what, Object... params);

        /**
         * api 调用是否成功
         * 
         * @return
         */
        public abstract boolean isCallApiSuccess(JSONObject result);
        
        public abstract void apiNetworkException(Exception e);
        
        /**
         * 获取缓存的地址,  返回null表示不缓存
         * 
         * @param what
         * @param params
         * @return
         */
        public String getCacheKey(int what, Object... params);
        

        /**
         * 接口调用成功的回调 what 一个activity调用多个api时what用于区分是那个api
         * 由于请求是异步的，所以如果是activity等生命周期组建要注意activity是否已经关闭了
         * 
         * @param what
         * @param result
         * @param isDone 返回的数据是不是最终数据
         * @param params
         */
        public abstract void handleResult(int what, JSONObject result,
                boolean isDone, Object... params);
        /**
         * 把from追加在to的结尾
         * 
         * @param from
         * @param to
         * @return
         */
        public JSONObject appendResult(int what, JSONObject from, JSONObject to);
        
        /**
         * 把from追加在to的开始
         * 
         * @param from
         * @param to
         * @return
         */
        public JSONObject prependResult(int what, JSONObject from, JSONObject to);
        
        /**
         * 
         * @param what
         * @param from
         * @param to
         * @return
         */
//        public JSONObject handleCache(int what, JSONObject from, JSONObject to);
    }
}