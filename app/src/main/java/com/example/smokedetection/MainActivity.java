package com.example.smokedetection;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private ImageView imgCameraPreview;
    private Button btnLogout, btnRefresh, btnHistory;
    private Button btnLiveStatus, btnLiveStart, btnLiveStop, btnLivePause, btnLiveResume, btnLiveSwitch;
    private Button btnClipSubmit, btnClipList, btnClipGet;
    private View cardCamera;
    private EditText edtSource, edtClipPath, edtClipJobId;
    private TextView txtRuntimeStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

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
        cardCamera = findViewById(R.id.cardCamera);
        btnLogout = findViewById(R.id.btnLogout);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnHistory = findViewById(R.id.btnHistory);
        btnLiveStatus = findViewById(R.id.btnLiveStatus);
        btnLiveStart = findViewById(R.id.btnLiveStart);
        btnLiveStop = findViewById(R.id.btnLiveStop);
        btnLivePause = findViewById(R.id.btnLivePause);
        btnLiveResume = findViewById(R.id.btnLiveResume);
        btnLiveSwitch = findViewById(R.id.btnLiveSwitch);
        btnClipSubmit = findViewById(R.id.btnClipSubmit);
        btnClipList = findViewById(R.id.btnClipList);
        btnClipGet = findViewById(R.id.btnClipGet);
        edtSource = findViewById(R.id.edtSource);
        edtClipPath = findViewById(R.id.edtClipPath);
        edtClipJobId = findViewById(R.id.edtClipJobId);
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

        btnRefresh.setOnClickListener(v -> refreshSnapshot());

        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        btnLiveStatus.setOnClickListener(v -> runAction("Live Status", cb -> ApiClient.getLiveStatus(cb)));
        btnLiveStart.setOnClickListener(v -> runAction("Live Start", cb -> ApiClient.liveStart(edtSource.getText().toString(), cb)));
        btnLiveStop.setOnClickListener(v -> runAction("Live Stop", cb -> ApiClient.liveStop(cb)));
        btnLivePause.setOnClickListener(v -> runAction("Live Pause", cb -> ApiClient.livePause(cb)));
        btnLiveResume.setOnClickListener(v -> runAction("Live Resume", cb -> ApiClient.liveResume(cb)));
        btnLiveSwitch.setOnClickListener(v -> {
            String source = edtSource.getText().toString().trim();
            if (source.isEmpty()) {
                Toast.makeText(this, "Enter source first", Toast.LENGTH_SHORT).show();
                return;
            }
            runAction("Live Switch", cb -> ApiClient.liveSwitchSource(source, cb));
        });

        btnClipSubmit.setOnClickListener(v -> {
            String clipPath = edtClipPath.getText().toString().trim();
            if (clipPath.isEmpty()) {
                Toast.makeText(this, "Enter clip path first", Toast.LENGTH_SHORT).show();
                return;
            }
            runAction("Clip Submit", cb -> ApiClient.submitClipPath(clipPath, cb));
        });
        btnClipList.setOnClickListener(v -> runAction("Clips List", cb -> ApiClient.listClipJobs(cb)));
        btnClipGet.setOnClickListener(v -> {
            String jobId = edtClipJobId.getText().toString().trim();
            if (jobId.isEmpty()) {
                Toast.makeText(this, "Enter job id first", Toast.LENGTH_SHORT).show();
                return;
            }
            runAction("Clip Get", cb -> ApiClient.getClipJob(jobId, cb));
        });

        btnLogout.setOnClickListener(v -> {
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
            stopService(serviceIntent);
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });

        runAction("Live Status", cb -> ApiClient.getLiveStatus(cb));
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
                    String shortBody = body.length() > 1000 ? body.substring(0, 1000) + "..." : body;
                    setStatus(label + " -> HTTP " + response.code() + "\n" + shortBody, response.code() >= 400);
                });
            }
        });
    }

    private void setStatus(String message, boolean isError) {
        txtRuntimeStatus.setText(message);
        txtRuntimeStatus.setTextColor(isError ? 0xFFD32F2F : 0xFF1B5E20);
    }
}