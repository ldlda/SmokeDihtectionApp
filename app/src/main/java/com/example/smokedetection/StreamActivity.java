package com.example.smokedetection;

import android.os.Bundle;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class StreamActivity extends AppCompatActivity {

    private WebView webView;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_stream);

        android.view.View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // Bind Views
        webView = findViewById(R.id.webViewStream);
        btnBack = findViewById(R.id.btnBack);

        // Configure WebView for Live Streaming
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false); // Hide the buttons

        // Load the Python Stream URL
        String streamUrl = ApiClient.getStreamUrl();
        webView.loadUrl(streamUrl);

        Toast.makeText(this, "Connecting to Camera...", Toast.LENGTH_SHORT).show();

        // Handle Back Button
        btnBack.setOnClickListener(v -> {
            // Stop loading to save battery/bandwidth
            webView.stopLoading();
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Make sure stream stops when we leave this screen
        if (webView != null) {
            webView.loadUrl("about:blank");
        }
    }
}