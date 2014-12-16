package com.yidianhulian.framework;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class Api {
	public static final String BOUNDARY = "-----------AndroidFormBoundar7d4a6d158c9";
	private String mApi;
	private Map<String, String> mQueryStr;
	private List<String> mFiles;
	private String mMethod;
	
	
	public Api(String method, String api, Map<String, String> queryStr, List<String> files){
	    this.mApi = api;
	    this.mQueryStr = queryStr;
	    this.mFiles = files;
	    this.mMethod = method;
	}
	public Api(String method, String api, Map<String, String> queryStr){
        this(method, api, queryStr, null);
    }
	
	public JSONObject invoke() throws NetworkException{
	    if("get".equalsIgnoreCase(mMethod))return this.get();
        return this.post();
	}

	public JSONObject post() throws NetworkException{
		JSONObject json = null;
		boolean hasFile = false;
		byte[] end_data = ("\r\n--" + BOUNDARY + "--\r\n").getBytes();
		if (mFiles != null) {
			hasFile = true;
		}
		StringBuffer contentBuffer = new StringBuffer();
		try{
			URL postUrl = new URL(mApi);

			mQueryStr.put("", "");
			HttpURLConnection connection = (HttpURLConnection) postUrl
					.openConnection();
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setRequestMethod("POST");
			connection.setUseCaches(false);
			connection.setInstanceFollowRedirects(true);
			if (hasFile) {
				connection.setRequestProperty("Content-type",
						"multipart/form-data; boundary=" + BOUNDARY);
				connection.setRequestProperty("Charset", "UTF-8");
			} else {
				connection.setRequestProperty("Content-Type",
						"application/x-www-form-urlencoded");
			}

			connection.connect();
			DataOutputStream out = new DataOutputStream(connection.getOutputStream());

			StringBuffer buffer = new StringBuffer();
			Iterator<Entry<String, String>> iterator = mQueryStr.entrySet().iterator();
			while (iterator.hasNext()) {
				Entry<String, String> entry = iterator.next();
				if (hasFile && mFiles.contains(entry.getKey())) {
					continue;
				}
				String value = entry.getValue();
				

				if (hasFile) {
					buffer.append("--");
					buffer.append(BOUNDARY);
					buffer.append("\r\n");
					buffer.append("Content-Disposition: form-data; name=\"");
					buffer.append(entry.getKey());

					buffer.append("\"\r\n\r\n");
					buffer.append(value);
					buffer.append("\r\n");
				} else {
					value = value != null ? URLEncoder.encode(value, "utf-8") : "";
					buffer.append(entry.getKey()).append("=").append(value).append("&");
				}
			}
			System.out.println(mApi);
			System.out.println(buffer.toString());
			if(hasFile){
				out.writeUTF(buffer.toString());
			}else{
				out.writeBytes(buffer.toString());
			}


			if (hasFile) {
				for (int i = 0; i < mFiles.size(); i++) {
					String fname = mQueryStr.get(mFiles.get(i));
					File file = new File(fname);
					StringBuilder sb = new StringBuilder();
					sb.append("--");
					sb.append(BOUNDARY);
					sb.append("\r\n");
					sb.append("Content-Disposition: form-data; name=\"");
					sb.append(mFiles.get(i));
					sb.append("\"; filename=\"");
					sb.append(file.getName());
					sb.append("\"\r\n");
					sb.append("Content-Type: application/octet-stream\r\n\r\n");

					out.write(sb.toString().getBytes());
					DataInputStream in = new DataInputStream(new FileInputStream(file));
					int bytes = 0;
					byte[] bufferOut = new byte[1024];
					while ((bytes = in.read(bufferOut)) != -1) {
						out.write(bufferOut, 0, bytes);
					}
					out.write("\r\n".getBytes());
					in.close();
					
				}
				out.write(end_data);
			}

			out.flush();
			out.close(); // flush and close

			try{
				BufferedReader reader = new BufferedReader(new InputStreamReader(
						connection.getInputStream(), "utf-8"));
				String inputLine = null;
				
				while ((inputLine = reader.readLine()) != null) {
					contentBuffer.append(inputLine);
				}
			}catch(Exception e){
				e.printStackTrace();
			}finally {
				connection.disconnect();
			}
            json = new JSONObject(contentBuffer.toString());
		}catch(IOException ioe){
		    throw new NetworkException(ioe);
		}catch (Exception e) {
		    System.out.println(contentBuffer.toString());
			e.printStackTrace();
		}
		return json;
	}

	public JSONObject get() throws NetworkException{
		java.net.URL url;
		JSONObject json = null;
		StringBuffer contentBuffer = new StringBuffer();
		StringBuffer buffer = new StringBuffer();
        Iterator<Entry<String, String>> iterator = mQueryStr.entrySet()
                .iterator();
        while (iterator.hasNext()) {
            Entry<String, String> entry = iterator.next();
            String value = entry.getValue();
            if (value != null) {
                try {
                    value = URLEncoder.encode(value, "utf-8");
                }catch(Exception e){}
            } else {
                value = "";
            }
            buffer.append(entry.getKey()).append("=").append(value)
                    .append("&");
        }
        
		try {
			url = new java.net.URL(mApi+"?"+buffer);
			Log.d("get", url.toString());
			
			java.net.URLConnection conn = url.openConnection();
			conn.connect();

			java.io.InputStream is = conn.getInputStream();
			java.io.BufferedReader reader = new java.io.BufferedReader(
					new java.io.InputStreamReader(is, "UTF-8"));
			String inputLine = null;
			while ((inputLine = reader.readLine()) != null) {
				contentBuffer.append(inputLine);
			}
			is.close();

			json = new JSONObject(contentBuffer.toString());

		} catch(IOException ioe){
            throw new NetworkException(ioe);
        }catch (Exception e) {
			Log.d("get-api-exception", contentBuffer.toString());
			e.printStackTrace();
		}
		return json;
	}

	//pragma static function
	
    public static Object getJSONValue(JSONObject json, String name){
        if(json==null)return null;
        try {
            return json.get(name);
        } catch (JSONException e) {
            return null;
        }
        
    }
    public static Integer getIntegerValue(JSONObject json, String name){
        Object o = Api.getJSONValue(json, name);
        if(o==null)return 0;
        try{
            return Integer.valueOf(String.valueOf(o));
        }catch(Exception e){
            return 0;
        }
    }
    public static String getStringValue(JSONObject json, String name){
        Object o = Api.getJSONValue(json, name);
        if(o==null)return null;
        return String.valueOf(o);
    }
    
    /**
     * 在明确知道返回值类型时使用
     * 
     * @param json
     * @param name
     * @param t
     * @return
     */
    public static <T> T getJSONValue(JSONObject json, String name, Class<T> t){
        Object o = Api.getJSONValue(json, name);
        if(o==null)return null;
        
        try {
            @SuppressWarnings("unchecked")
            T o2 = (T)o;

            return o2;
          
        } catch (Exception e1) {
            return null;
        }
    }
    
    class NetworkException extends Exception{
        private static final long serialVersionUID = 1L;

        public NetworkException(Throwable throwable) {
            super(throwable);
        }
        
    }
}
