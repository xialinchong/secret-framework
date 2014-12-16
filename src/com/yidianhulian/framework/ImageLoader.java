package com.yidianhulian.framework;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.net.URL;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

/**
 * 
 * 
 * @author leeboo
 *
 */
public class ImageLoader {
	private Context mContext;
	MyHandler mHandler = new MyHandler();
	
	public ImageLoader(Context context) {
	    mContext = context;
	}
	
	private BitmapDrawable cachedImage(final String imageUrl){
	   File dir = mContext.getExternalCacheDir();
	   if(dir == null)return null;
	   
	   if(! dir.exists()){  
           return null;
       }  
	   File[] files = dir.listFiles(new FilenameFilter(){
	       @Override
            public boolean accept(File dir, String filename) {
                return filename.equals(Util.MD5(imageUrl));
            }
	   });
	   if(files==null || files.length==0)return null;
	   
       Bitmap bm = BitmapFactory.decodeFile(files[0].getAbsolutePath());
       return new BitmapDrawable(mContext.getResources(), bm);
	}
	
	public void loadImage(ImageView imageView, String imageUrl){
	    loadImage(imageView, imageUrl, new ImageLoaded() {
            @Override
            public void imageLoaded(ImageView imageView, Drawable imageDrawable) {
                if(imageDrawable!=null){
                    imageView.setImageDrawable(imageDrawable);
                }
            }
        });
	}
	
	public void loadImage(final ImageView imageView, final String imageUrl, final ImageLoaded imageCallback) {
	    new Thread("loadDrawable"){
	        public void run(){
        	    Drawable cached = cachedImage(imageUrl);
                if (cached != null) {
                    mHandler.obtainMessage(0, new Object[]{imageView, cached, imageCallback}).sendToTarget();
                    return;
                }
        
                new Thread("loadImageFromUrl") {
                    @Override
                    public void run() {
                        BitmapDrawable drawable = loadImageFromUrl(imageUrl);
                        if(drawable!=null){
                            try {
                                File dirFile = mContext.getExternalCacheDir();  
                                if(dirFile != null){
                                    if( ! dirFile.exists()){  
                                        dirFile.mkdir();  
                                    }  
                                    
                                    File cacheFile = new File(dirFile.getAbsolutePath() + "/" + Util.MD5(imageUrl));  
                                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cacheFile));
                                      
                                    drawable.getBitmap().compress(Bitmap.CompressFormat.PNG, 100, bos);  
                                    bos.flush();  
                                    bos.close(); 
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        mHandler.obtainMessage(0, new Object[]{imageView, drawable, imageCallback}).sendToTarget();
                    }
                }.start();
	        }
        }.start();
	}
	

	private BitmapDrawable loadImageFromUrl(String url) {
		if(url==null || "".equals(url.trim()))return null;

		URL u;
		InputStream i = null;
		BitmapDrawable d = null;
		try {
			u = new URL(url);
			i = (InputStream) u.getContent();
			d = new BitmapDrawable(mContext.getResources(), i);
			d.setTargetDensity(mContext.getResources().getDisplayMetrics());
			i.close();
			
		} catch (Exception e) {
			Log.d("loadImageFromUrl", url+":"+e.getMessage());
		}
		Log.d("loading images", url+": "+(d!=null ? "ok" : "fail"));
		return d;
	}

	public interface ImageLoaded {
	    /**
	     * 
	     * @param imageView
	     * @param imageDrawable null表示加载失败
	     */
		public void imageLoaded(ImageView imageView, Drawable imageDrawable);
	}
	
	static class MyHandler extends Handler{
		public void handleMessage(Message message) {
			Object[] datas 	     = (Object[])message.obj;
			ImageView imageView  = (ImageView)datas[0];
			Drawable  drawable   = (Drawable)datas[1];
			ImageLoaded  callback  = (ImageLoaded)datas[2];
			
			if(callback == null)return;
			callback.imageLoaded(imageView, drawable);
		
		}
	};
}
