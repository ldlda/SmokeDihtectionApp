package com.example.smokedetection;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

public class MainActivity extends AppCompatActivity {

    private ImageView imgCameraPreview;
    private Button btnLogout, btnRefresh, btnHistory; // Added btnHistory here
    private View cardCamera;

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

        btnLogout.setOnClickListener(v -> {
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
            stopService(serviceIntent);
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void refreshSnapshot() {
        Glide.with(this)
                .load(ApiClient.getSnapshotUrl())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(android.R.drawable.ic_menu_camera)
                .into(imgCameraPreview);
    }
}