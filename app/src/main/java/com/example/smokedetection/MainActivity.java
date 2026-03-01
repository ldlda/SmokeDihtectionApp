package com.example.smokedetection;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends AppCompatActivity {

    private ImageView imgCameraPreview;
    private Button btnLogout, btnRefresh, btnHistory, btnOpenLive;
    private Button btnClipPickUpload, btnPhoneLiveStart;
    private TextView txtRuntimeStatus;
    private ActivityResultLauncher<String> clipPickerLauncher;
    private WebSocket updatesSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        clipPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onClipPicked);

        // Ensure ApiClient has the saved server URL (covers skip-login path)
        ApiClient.init(this);

        android.view.View root = findViewById(R.id.root);
        int pl = root.getPaddingLeft(), pt = root.getPaddingTop(),
                pr = root.getPaddingRight(), pb = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(pl + insets.left, pt + insets.top, pr + insets.right, pb + insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // Permission check (For Notifications on Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // Bind all views
        imgCameraPreview = findViewById(R.id.imgCameraPreview);
        View cardCamera = findViewById(R.id.cardCamera);
        btnLogout = findViewById(R.id.btnLogout);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnHistory = findViewById(R.id.btnHistory);
        btnOpenLive = findViewById(R.id.btnOpenLive);
        btnClipPickUpload = findViewById(R.id.btnClipPickUpload);
        btnPhoneLiveStart = findViewById(R.id.btnPhoneLiveStart);
        txtRuntimeStatus = findViewById(R.id.txtRuntimeStatus);

        // Start background service safely
        Intent serviceIntent = new Intent(this, SmokeAlert.class);
        // This check prevents crash on newer Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Load initial snapshot
        refreshSnapshot();

        // Handle clicks
        cardCamera.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, StreamActivity.class);
            startActivity(intent);
        });
        btnOpenLive.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, StreamActivity.class);
            startActivity(intent);
        });

        btnRefresh.setOnClickListener(v -> refreshSnapshot());

        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        btnClipPickUpload.setOnClickListener(v -> clipPickerLauncher.launch("*/*"));
        btnPhoneLiveStart.setOnClickListener(v -> runAction("Phone Live", cb -> ApiClient.startPhoneLive(cb)));

        btnLogout.setOnClickListener(v -> {
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
            stopService(serviceIntent);
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });

        runAction("Ready", cb -> ApiClient.getLiveStatus(cb));
    }

    private void refreshSnapshot() {
        Glide.with(this)
                .load(ApiClient.getSnapshotUrl())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(android.R.drawable.ic_menu_camera)
                .into(imgCameraPreview);
    }

    private interface ActionRunner {
        void run(Callback callback);
    }

    private void runAction(String label, ActionRunner actionRunner) {
        setStatus(label + "...", false);
        actionRunner.run(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> setStatus(label + " failed: " + e.getMessage(), true));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    if (response.code() >= 400) {
                        String shortBody = body.length() > 500 ? body.substring(0, 500) + "..." : body;
                        setStatus(label + " failed (" + response.code() + ")\n" + shortBody, true);
                    } else {
                        setStatus(label + " successful", false);
                    }
                });
            }
        });
    }

    private void connectUpdatesSocket() {
        closeUpdatesSocket();
        updatesSocket = ApiClient.openUpdatesWebSocket(new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                runOnUiThread(() -> setStatus("Connected to live updates", false));
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                runOnUiThread(() -> renderSnapshotStatus(text));
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                runOnUiThread(() -> setStatus("Live updates disconnected. Check server/network.", true));
            }
        });
    }

    private void closeUpdatesSocket() {
        if (updatesSocket != null) {
            updatesSocket.close(1000, "screen hidden");
            updatesSocket = null;
        }
    }

    private void renderSnapshotStatus(String payload) {
        try {
            JSONObject root = new JSONObject(payload);
            JSONObject live = root.optJSONObject("live");

            String statusLine = "Live status unavailable";
            boolean isError = false;
            if (live != null) {
                boolean starting = live.optBoolean("starting", false);
                boolean running = live.optBoolean("running", false);
                boolean paused = live.optBoolean("paused", false);
                String source = live.optString("source", "");
                String lastError = live.optString("last_error", "");

                if (starting) {
                    statusLine = "Starting camera...";
                } else if (paused) {
                    statusLine = "Live is paused";
                } else if (running) {
                    statusLine = "Live is running";
                    if (!source.isEmpty()) {
                        statusLine += " (source: " + source + ")";
                    }
                } else {
                    statusLine = "Live is stopped";
                }

                if (!lastError.isEmpty() && !"null".equalsIgnoreCase(lastError)) {
                    isError = true;
                    statusLine += "\nIssue: " + lastError;
                }
            }

            int historyCount = root.optInt("history_count", -1);
            if (historyCount >= 0) {
                statusLine += "\nAlerts in history: " + historyCount;
            }
            setStatus(statusLine, isError);
        } catch (Exception e) {
            setStatus("Connected, waiting for updates...", false);
        }
    }

    private void setStatus(String message, boolean isError) {
        txtRuntimeStatus.setText(message);
        txtRuntimeStatus.setTextColor(isError ? 0xFFD32F2F : 0xFF1B5E20);
    }

    private void onClipPicked(Uri uri) {
        if (uri == null) return;
        runAction("Upload Clip", cb -> ApiClient.submitClipFile(this, uri, "mobile_upload", cb));
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectUpdatesSocket();
    }

    @Override
    protected void onStop() {
        closeUpdatesSocket();
        super.onStop();
    }
}