package com.example.smokedetection;

import android.os.Bundle;
import android.webkit.WebView;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ViewEvidenceActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_evidence);

        android.view.View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        WebView webView = findViewById(R.id.webViewImage);
        Button btnClose = findViewById(R.id.btnClose);

        // Enable Zoom
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        // Make image fit screen
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        // Load URL
        String imageUrl = getIntent().getStringExtra("IMAGE_URL");
        if (imageUrl != null) {
            if (isVideoUrl(imageUrl)) {
                String html = "<html><body style='margin:0;background:black;display:flex;align-items:center;justify-content:center;height:100vh;'>"
                        + "<video controls autoplay playsinline style='width:100%;height:100%;object-fit:contain;'>"
                        + "<source src='" + imageUrl + "' type='video/mp4'>"
                        + "</video></body></html>";
                webView.loadDataWithBaseURL(imageUrl, html, "text/html", "utf-8", null);
            } else {
                webView.loadUrl(imageUrl);
            }
        }

        // Close
        btnClose.setOnClickListener(v -> finish());
    }

    private boolean isVideoUrl(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".mp4")
                || lower.endsWith(".webm")
                || lower.endsWith(".mov")
                || lower.endsWith(".mkv");
    }
}