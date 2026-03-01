package com.example.smokedetection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class StreamActivity extends AppCompatActivity {

    private WebView webView;
    private Button btnBack;
    private ExecutorService cameraExecutor;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private final AtomicBoolean frameSocketReady = new AtomicBoolean(false);
    private volatile long lastUploadMs = 0L;
    private static final long MIN_UPLOAD_INTERVAL_MS = 33;
    private ProcessCameraProvider cameraProvider;
    private WebSocket framePushSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_stream);

        cameraExecutor = Executors.newSingleThreadExecutor();
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startPhoneLiveAndCamera();
                    } else {
                        Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
                    }
                }
        );

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
        WebSettings settings = webView.getSettings();
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Load the Python Stream URL
        String streamUrl = ApiClient.getStreamUrl();
        webView.loadUrl(streamUrl);

        Toast.makeText(this, "Starting phone live stream...", Toast.LENGTH_SHORT).show();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startPhoneLiveAndCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }

        // Handle Back Button
        btnBack.setOnClickListener(v -> {
            // Stop loading to save battery/bandwidth
            webView.stopLoading();
            finish();
        });
    }

    private void startPhoneLiveAndCamera() {
        ApiClient.startPhoneLive(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(StreamActivity.this,
                        "Phone live start failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) {
                response.close();
                openFramePushSocket();
                startCameraPushLoop();
            }
        });
    }

    private void openFramePushSocket() {
        closeFramePushSocket();
        framePushSocket = ApiClient.openFramePushWebSocket(new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                frameSocketReady.set(true);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                frameSocketReady.set(false);
                runOnUiThread(() -> Toast.makeText(StreamActivity.this,
                        "Frame WebSocket disconnected", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                frameSocketReady.set(false);
            }
        });
    }

    private void closeFramePushSocket() {
        frameSocketReady.set(false);
        WebSocket socket = framePushSocket;
        framePushSocket = null;
        if (socket != null) {
            socket.close(1000, "stream closed");
        }
    }

    private void startCameraPushLoop() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                analysis.setAnalyzer(cameraExecutor, image -> {
                    long now = System.currentTimeMillis();
                    if (now - lastUploadMs < MIN_UPLOAD_INTERVAL_MS) {
                        image.close();
                        return;
                    }

                    if (!frameSocketReady.get()) {
                        image.close();
                        return;
                    }

                    byte[] jpeg = imageToJpeg(image);
                    image.close();
                    if (jpeg == null) {
                        return;
                    }

                    WebSocket socket = framePushSocket;
                    if (socket == null) {
                        return;
                    }

                    boolean sent = socket.send(ByteString.of(jpeg));
                    if (sent) {
                        lastUploadMs = now;
                    }
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        analysis
                );
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(StreamActivity.this,
                        "Camera start failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private byte[] imageToJpeg(ImageProxy image) {
        if (image.getPlanes().length == 0) {
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        ImageProxy.PlaneProxy rgbaPlane = image.getPlanes()[0];
        ByteBuffer rgbaBuffer = rgbaPlane.getBuffer();
        int rowStride = rgbaPlane.getRowStride();
        int pixelStride = rgbaPlane.getPixelStride();

        byte[] rgba = new byte[width * height * 4];
        int outOffset = 0;
        for (int row = 0; row < height; row++) {
            int rowStart = row * rowStride;
            for (int col = 0; col < width; col++) {
                int index = rowStart + (col * pixelStride);
                rgba[outOffset++] = rgbaBuffer.get(index);
                rgba[outOffset++] = rgbaBuffer.get(index + 1);
                rgba[outOffset++] = rgbaBuffer.get(index + 2);
                rgba[outOffset++] = rgbaBuffer.get(index + 3);
            }
        }

        int[] argb = new int[width * height];
        int p = 0;
        for (int i = 0; i < rgba.length; i += 4) {
            int r = rgba[i] & 0xFF;
            int g = rgba[i + 1] & 0xFF;
            int b = rgba[i + 2] & 0xFF;
            int a = rgba[i + 3] & 0xFF;
            argb[p++] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(argb, 0, width, 0, 0, width, height);

        int rotationDegrees = image.getImageInfo().getRotationDegrees();
        Bitmap rotated = bitmap;
        if (rotationDegrees != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            rotated = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );
        }

        ByteArrayOutputStream rotatedOut = new ByteArrayOutputStream();
        rotated.compress(Bitmap.CompressFormat.JPEG, 60, rotatedOut);

        if (rotated != bitmap) {
            bitmap.recycle();
        }
        rotated.recycle();
        return rotatedOut.toByteArray();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Make sure stream stops when we leave this screen
        if (webView != null) {
            webView.loadUrl("about:blank");
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdownNow();
        }
        closeFramePushSocket();
    }
}