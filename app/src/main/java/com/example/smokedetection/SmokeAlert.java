package com.example.smokedetection;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SmokeAlert extends Service {

    // Configuration: How often to check the server (in milliseconds)
    private static final int CHECK_INTERVAL = 5000;

    private Handler handler = new Handler();
    private OkHttpClient client = new OkHttpClient();
    private String lastAlertTimestamp = ""; // Keeps track of the last alert we saw
    // The Loop
    private final Runnable checkServerRunnable = new Runnable() {
        @Override
        public void run() {
            checkServerForSmoke();
            // Schedule this same function to run again in 5 seconds
            handler.postDelayed(this, CHECK_INTERVAL);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Start this service in the "Foreground"
        // Put a permanent notification in the status bar so Android knows
        // this app is important and shouldn't be killed to save battery.
        startForeground(1, getNotification("Monitoring active..."));

        // Start the repeating check loop
        handler.post(checkServerRunnable);
    }

    private void checkServerForSmoke() {
        // Build the request URL
        String url = ApiClient.getHistoryUrl();
        Request request = new Request.Builder().url(url).build();

        // Send request to server
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Silent fail: If internet is down, don't annoy the user
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) return;

                try {
                    String body = response.body().string();
                    JSONArray alerts = new JSONArray(body);

                    // If we have any alerts in the history
                    if (alerts.length() > 0) {
                        // Get the most recent one (Item 0)
                        JSONObject latest = alerts.getJSONObject(0);
                        String timestamp = latest.getString("timestamp");

                        // Check if this timestamp is different from the last one we saw
                        if (!timestamp.equals(lastAlertTimestamp)) {

                            // Only trigger alert if this isn't the first time the app loaded
                            // --> Not beep immediately just for loading old history
                            if (!lastAlertTimestamp.isEmpty()) {
                                triggerDangerNotification();
                            }

                            // Update our memory so we don't alert again for this same event
                            lastAlertTimestamp = timestamp;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
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
        // Stop the loop when service is destroyed
        handler.removeCallbacks(checkServerRunnable);
        super.onDestroy();
    }
}