package com.example.smokedetection;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class SmokeAlert extends Service {

    private static final int RECONNECT_DELAY_MS = 3000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient();
    private WebSocket updatesSocket;
    private boolean serviceStopped = false;
    private String lastAlertTimestamp = ""; // Keeps track of the last alert we saw
    private boolean initializedFromHistory = false;
    private final Runnable reconnectRunnable = this::connectUpdatesSocket;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Start this service in the "Foreground"
        // Put a permanent notification in the status bar so Android knows
        // this app is important and shouldn't be killed to save battery.
        startForeground(1, getNotification("Monitoring active..."));

        connectUpdatesSocket();
    }

    private void connectUpdatesSocket() {
        if (serviceStopped) {
            return;
        }

        closeUpdatesSocket();

        Request request = new Request.Builder()
                .url(ApiClient.getUpdatesWebSocketUrl())
                .build();

        updatesSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                handler.removeCallbacks(reconnectRunnable);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleSnapshotMessage(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                scheduleReconnect();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                scheduleReconnect();
            }
        });
    }

    private void handleSnapshotMessage(String payload) {
        try {
            JSONObject root = new JSONObject(payload);
            JSONArray alerts = root.optJSONArray("history");
            if (alerts == null || alerts.length() == 0) {
                if (!initializedFromHistory) {
                    initializedFromHistory = true;
                }
                return;
            }

            JSONObject latest = alerts.getJSONObject(0);
            String timestamp = latest.optString("timestamp", "");
            if (timestamp.isEmpty()) {
                return;
            }

            if (!initializedFromHistory) {
                lastAlertTimestamp = timestamp;
                initializedFromHistory = true;
                return;
            }

            if (!timestamp.equals(lastAlertTimestamp)) {
                triggerDangerNotification();
                lastAlertTimestamp = timestamp;
            }
        } catch (Exception ignored) {
        }
    }

    private void scheduleReconnect() {
        if (serviceStopped) {
            return;
        }
        handler.removeCallbacks(reconnectRunnable);
        handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
    }

    private void closeUpdatesSocket() {
        if (updatesSocket != null) {
            updatesSocket.close(1000, "service stop");
            updatesSocket = null;
        }
    }

    private void triggerDangerNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);

        // Create a High Priority notification that pops up
        Notification notification = new Notification.Builder(this, "SMOKE_CHANNEL_ID")
                .setContentTitle("SmokeAlert")
                .setContentText("ALERT: Smoke Detected!")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setAutoCancel(true) // Disappear when clicked
                .build();

        manager.notify(2, notification);
    }

    private Notification getNotification(String text) {
        // Create the persistent "Monitoring" notification
        return new Notification.Builder(this, "SMOKE_CHANNEL_ID")
                .setContentTitle("SmokeAlert")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
    }

    private void createNotificationChannel() {
        // Required for Android 8.0 and above
        NotificationChannel channel = new NotificationChannel(
                "SMOKE_CHANNEL_ID",
                "SmokeAlert Notifications",
                NotificationManager.IMPORTANCE_HIGH
        );
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If the system kills the app, restart this service automatically
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        serviceStopped = true;
        handler.removeCallbacks(reconnectRunnable);
        closeUpdatesSocket();
        super.onDestroy();
    }
}