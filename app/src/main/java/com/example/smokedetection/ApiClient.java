package com.example.smokedetection;

import okhttp3.*;
import java.io.IOException;

public class ApiClient {
    public static final String BASE_URL = "http://lda.local:8000"; // fuck this

    private static final OkHttpClient client = new OkHttpClient();

    // Authentication

    public static void login(String username, String password, Callback callback) {
        // Create the form body with data
        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();

        // Prepare the request
        Request request = new Request.Builder()
                .url(BASE_URL + "/login")
                .post(formBody)
                .build();

        // Send it in the background
        client.newCall(request).enqueue(callback);
    }

    public static void register(String username, String password, Callback callback) {
        // Create the form body with data
        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build();

        // Prepare the request
        Request request = new Request.Builder()
                .url(BASE_URL + "/register")
                .post(formBody)
                .build();

        // Send it in the background
        client.newCall(request).enqueue(callback);
    }

    // Camera URL
    // These return strings because we load them into Glide/WebView directly

    public static String getSnapshotUrl() {
        return BASE_URL + "/snapshot";
    }

    public static String getStreamUrl() {
        return BASE_URL + "/video_feed";
    }
}