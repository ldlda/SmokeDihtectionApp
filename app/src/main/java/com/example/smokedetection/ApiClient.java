package com.example.smokedetection;

import android.content.Context;
import android.content.SharedPreferences;

import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class ApiClient {
    private static final String PREFS = "AppPrefs";
    private static final String KEY_SERVER = "server_url";
    private static final String DEFAULT_URL = "http://lda.local:8000";
    private static final OkHttpClient client = new OkHttpClient();
    private static String baseUrl = DEFAULT_URL;

    /**
     * Call once from the launcher activity to load the saved URL.
     */
    public static void init(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        baseUrl = prefs.getString(KEY_SERVER, DEFAULT_URL);
    }

    /**
     * Persist a new server URL.
     */
    public static void setServer(Context ctx, String url) {
        // Normalise: strip trailing slash
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        baseUrl = url;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SERVER, url).apply();
    }

    public static String getBaseUrl() {
        return baseUrl;
    }

    // Authentication

    public static void login(String username, String password, Callback callback) {
        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();

        Request request = new Request.Builder()
                .url(baseUrl + "/login")
                .post(formBody)
                .build();

        client.newCall(request).enqueue(callback);
    }

    public static void register(String username, String password, Callback callback) {
        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();

        Request request = new Request.Builder()
                .url(baseUrl + "/register")
                .post(formBody)
                .build();

        client.newCall(request).enqueue(callback);
    }

    /**
     * Ping the server to verify it's reachable.
     */
    public static void ping(Callback callback) {
        Request request = new Request.Builder()
                .url(baseUrl + "/ping")
                .build();
        client.newCall(request).enqueue(callback);
    }

    // Camera URLs

    public static String getSnapshotUrl() {
        return baseUrl + "/snapshot";
    }

    public static String getStreamUrl() {
        return baseUrl + "/video_feed";
    }
}