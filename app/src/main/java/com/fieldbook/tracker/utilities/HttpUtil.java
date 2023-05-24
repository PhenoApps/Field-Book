package com.fieldbook.tracker.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HttpUtil {
    private HttpURLConnection httpConn;
    private Map<String, Object> queryParams;

    private String contentType;

    private Context context;

    private static Handler handler = new Handler(Looper.getMainLooper());

    public void showToast(final int resId) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 构造方法
     *
     * @param requestURL  请求地址
     * @param headers     请求头
     * @param queryParams 请求字段
     * @throws IOException
     */
    public HttpUtil(Context context, String requestURL, Map<String, String> headers, Map<String, Object> queryParams,String contentType) throws IOException {
        this.context = context;
        if (queryParams == null) {
            this.queryParams = new HashMap<>();
        } else {
            this.queryParams = queryParams;
        }
        System.out.println("requestURL: " + "http://" + requestURL);
        URL url = new URL("http://" + requestURL);
        httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setUseCaches(false);
        httpConn.setDoOutput(true);    // 表明是post请求
        httpConn.setDoInput(true);

        SharedPreferences sharedPreferences = this.context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE);
        String gosToken = sharedPreferences.getString(GeneralKeys.GOS_TOKEN, null);

        if(gosToken != null) {
            System.out.println("已有gosToken: " + gosToken);
            httpConn.setRequestProperty("Cookie", gosToken);
        }
        System.out.println("contentType: " + this.contentType);
        if (contentType == null) {
            httpConn.setRequestProperty("Content-Type", "application/json");
        } else {
            httpConn.setRequestProperty("Content-Type", contentType);
        }
        if (headers != null && headers.size() > 0) {
            Iterator<String> it = headers.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                String value = headers.get(key);
                httpConn.setRequestProperty(key, value);
            }
        }
    }

    public HttpUtil(Context context, String requestURL,String contentType) throws IOException {
        this(context, requestURL, null, null,contentType);
    }

    /**
     * 添加参数字段
     *
     * @param name
     * @param value
     */
    public void addFormField(String name, Object value) {
        queryParams.put(name, value);
    }

    /**
     * 添加请求头
     *
     * @param key
     * @param value
     */
    public void addHeader(String key, String value) {
        httpConn.setRequestProperty(key, value);
    }

    /**
     * 将请求字段转化成byte数组
     *
     * @param params
     * @return
     */
    private byte[] getParamsByte(Map<String, Object> params) {
        byte[] result = null;
        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String, Object> param : params.entrySet()) {
            if (postData.length() != 0) {
                postData.append('&');
            }
            postData.append(this.encodeParam(param.getKey()));
            postData.append('=');
            postData.append(this.encodeParam(String.valueOf(param.getValue())));
        }
        try {
            result = postData.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 对键和值进行url编码
     *
     * @param data
     * @return
     */
    private String encodeParam(String data) {
        String result = "";
        try {
            result = URLEncoder.encode(data, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 完成请求，并接受服务器的回应
     *
     * @return 如果请求成功，状态码是200，返回服务器返回的字符串，否则抛出异常
     * @throws IOException
     */
    public JsonObject finish() throws IOException {
        System.out.println("调用接口"+httpConn.getURL());
        String response = "";
        System.out.println(queryParams);
        byte[] postDataBytes = this.getParamsByte(queryParams);
        httpConn.getOutputStream().write(postDataBytes);
        int status = httpConn.getResponseCode();
        if (status == HttpURLConnection.HTTP_OK) {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = httpConn.getInputStream().read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            String cookie = httpConn.getHeaderField("Set-Cookie");
            if (cookie != null) {
                cookie = cookie.substring(0, cookie.indexOf(";"));
                SharedPreferences preferences = context
                        .getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(GeneralKeys.GOS_TOKEN, cookie);
                editor.apply();
            }
            response = result.toString("utf-8");
            System.out.println(response);
            Gson gson = new Gson();
            JsonObject json = gson.fromJson(response, JsonObject.class);
            httpConn.disconnect();
            if (json.get("code").getAsInt() != 200) {
                showToast(R.string.gos_authorize_failed_403);
                return null;
            }else{
                return json;
            }
        } else if (status == HttpURLConnection.HTTP_FORBIDDEN) {
            System.out.println("未登录,调用登录接口");
            SharedPreferences sharedPreferences = this.context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE);
            String gosUrl = sharedPreferences.getString(GeneralKeys.GOS_BASE_URL, null);
            String gosPort = sharedPreferences.getString(GeneralKeys.GOS_PORT, null);
            String username = sharedPreferences.getString(GeneralKeys.GOS_USERNAME, null);
            String password = sharedPreferences.getString(GeneralKeys.GOS_PASSWORD, null);
            System.out.println(username + " " + password );
            if (gosUrl.length() == 0 || gosPort.length() == 0 || username.length() == 0 || password.length() == 0 ) {
                showToast(R.string.gos_check_setting);
            }
            password = HttpUtil.md5Password(password);
            HttpUtil httpUtil = new HttpUtil(context, gosUrl + ":" + gosPort + "/web/login","application/x-www-form-urlencoded");
            // post参数
            httpUtil.addFormField("username", username);
            //md5加密
            httpUtil.addFormField("password", password);
            httpUtil.addFormField("captchaVerification", "sLkNRVNRCVNyxglc3qusQwdREkmuGy3JZJ51q69cQKZyKSyRBwsBpTQpCx5ZrIkS");
            // 返回信息
            JsonObject json = httpUtil.finish();
            if (json != null) {
                String cookie = httpConn.getHeaderField("Set-Cookie");
                cookie = cookie.substring(0, cookie.indexOf(";"));
                SharedPreferences preferences = context
                        .getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(GeneralKeys.GOS_TOKEN, cookie);
                editor.apply();
                System.out.println("登录成功"+cookie);
                return finish();
            }else{
                showToast(R.string.gos_authorize_failed_403);
                return null;
            }
        } else {
            showToast(R.string.gos_authorize_failed_timeout);
            return null;
        }
    }

    public static String md5Password(String password) {
        StringBuffer sb = new StringBuffer();
        // 得到一个信息摘要器
        try {
            MessageDigest digest = MessageDigest.getInstance("md5");
            byte[] result = digest.digest(password.getBytes());
            // 把每一个byte做一个与运算 0xff
            for (byte b : result) {
                // 与运算
                int number = b & 0xff;
                String str = Integer.toHexString(number);
                if (str.length() == 1) {
                    sb.append("0");
                }
                sb.append(str);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}