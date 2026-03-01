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
        // Normalize: strip trailing slash
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        baseUrl = url;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SERVER, url).apply();
    }

    public static String getBaseUrl() {
        return baseUrl;
    }

    public static String endpoint(String path) {
        if (path.startsWith("/")) return baseUrl + path;
        return baseUrl + "/" + path;
    }

    public static String getHistoryUrl() {
        return getHistoryUrl(null);
    }

    public static String getHistoryUrl(Integer userId) {
        if (userId == null) return endpoint("/history");
        return endpoint("/history?user_id=" + userId);
    }

    public static RequestBody buildClearHistoryBody(Integer userId) {
        FormBody.Builder builder = new FormBody.Builder();
        if (userId != null) {
            builder.add("user_id", String.valueOf(userId));
        }
        return builder.build();
    }

    public static String getClearHistoryUrl() {
        return endpoint("/clear_history");
    }

    public static String resolveMediaUrl(String imagePath) {
        return endpoint(imagePath);
    }

    // Authentication

    public static void login(String username, String password, Callback callback) {
        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();

        Request request = new Request.Builder()
            .url(endpoint("/login"))
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
            .url(endpoint("/register"))
                .post(formBody)
                .build();

        client.newCall(request).enqueue(callback);
    }

    /**
     * Ping the server to verify it's reachable.
     */
    public static void ping(Callback callback) {
        Request request = new Request.Builder()
            .url(endpoint("/ping"))
                .build();
        client.newCall(request).enqueue(callback);
    }

    // Camera URLs

    public static String getSnapshotUrl() {
        return endpoint("/snapshot");
    }

    public static String getStreamUrl() {
        return endpoint("/video_feed");
    }

    // Live runtime endpoints

    public static void getLiveStatus(Callback callback) {
        Request request = new Request.Builder()
                .url(endpoint("/live/status"))
                .build();
        client.newCall(request).enqueue(callback);
    }

    public static void liveStart(String source, Callback callback) {
        FormBody.Builder body = new FormBody.Builder();
        if (source != null && !source.trim().isEmpty()) {
            body.add("source", source.trim());
        }
        Request request = new Request.Builder()
                .url(endpoint("/live/start"))
                .post(body.build())
                .build();
        client.newCall(request).enqueue(callback);
    }

    public static void liveStop(Callback callback) {
        Request request = new Request.Builder()
                .url(endpoint("/live/stop"))
                .post(new FormBody.Builder().build())
                .build();
        client.newCall(request).enqueue(callback);
    }

    public static void livePause(Callback callback) {
        Request request = new Request.Builder()
                .url(endpoint("/live/pause"))
                .post(new FormBody.Builder().build())
                .build();
        client.newCall(request).enqueue(callback);
    }

    public static void liveResume(Callback callback) {
        Request request = new Request.Builder()
                .url(endpoint("/live/resume"))
                .post(new FormBody.Builder().build())
                .build();
        client.newCall(request).enqueue(callback);
    }

    public static void liveSwitchSource(String source, Callback callback) {
        RequestBody body = new FormBody.Builder()
                .add("source", source)
                .build();
        Request request = new Request.Builder()
                .url(endpoint("/live/switch_source"))
                .post(body)
                .build();
        client.newCall(request).enqueue(callback);
    }

    // Clip job endpoints

    public static void submitClipPath(String path, Callback callback) {
        RequestBody body = new FormBody.Builder()
                .add("path", path)
                .build();
        Request request = new Request.Builder()
                .url(endpoint("/clips/submit"))
                .post(body)
                .build();
        client.newCall(request).enqueue(callback);
    }

    public static void listClipJobs(Callback callback) {
        Request request = new Request.Builder()
                .url(endpoint("/clips"))
                .build();
        client.newCall(request).enqueue(callback);
    }

    public static void getClipJob(String jobId, Callback callback) {
        Request request = new Request.Builder()
                .url(endpoint("/clips/" + jobId))
                .build();
        client.newCall(request).enqueue(callback);
    }
}