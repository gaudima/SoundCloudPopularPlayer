package com.gaudima.gaudima.soundcloudplayer.api;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.LruCache;


import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;

public class Api {
    private static final String TAG = "Api";
    private static Api instance;
    private static Context context;

    private RequestQueue requestQueue;
    private ImageLoader imageLoader;
    private String client_id = "da162cfea10ce28a8ad2a5eea97bf158";
    private String client_secret = "68b8d60dcc35472ab81eb36197f7e544";

    private Api(Context ctx) {
        context = ctx;
        requestQueue = getRequestQueue();
        imageLoader = new ImageLoader(requestQueue, new ImageLoader.ImageCache() {
            private final LruCache<String, Bitmap> cache = new LruCache<>(100);

            @Override
            public void putBitmap(String url, Bitmap bitmap) {
                cache.put(url, bitmap);
            }

            @Override
            public Bitmap getBitmap(String url) {
                return cache.get(url);
            }
        });
    }

    public String getApiUrlWithParams(String url, String ...params) {
        StringBuilder stringBuilder = ApiUtils.getStringBuilder();
        stringBuilder.append(url).append("?").append("client_id=").append(client_id);
        for(int i = 0; i < params.length; i += 2) {
            stringBuilder.append("&");
            stringBuilder.append(params[i]).append("=").append(params[i+1]);
        }
        String result = stringBuilder.toString();
        ApiUtils.releaseStringBuilder(stringBuilder);
        Log.d(TAG, result);
        return result;
    }

    public RequestQueue getRequestQueue() {
        if(requestQueue == null) {
            requestQueue = Volley.newRequestQueue(context.getApplicationContext());
        }
        return requestQueue;
    }

    public static synchronized Api getInstance(Context ctx) {
        if(instance == null) {
            instance = new Api(ctx);
        }
        return instance;
    }

    public <T> void addToRequestQueue(Request<T> request) {
        getRequestQueue().add(request);
    }

    public ImageLoader getImageLoader() {
        return imageLoader;
    }
}
