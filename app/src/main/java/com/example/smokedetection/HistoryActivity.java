package com.example.smokedetection;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HistoryActivity extends AppCompatActivity {

    private final OkHttpClient client = new OkHttpClient();
    private RecyclerView recyclerHistory;
    private TextView txtEmpty;
    private ImageButton btnBack, btnClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);

        android.view.View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // Bind Views
        recyclerHistory = findViewById(R.id.recyclerHistory);
        txtEmpty = findViewById(R.id.txtEmpty);
        btnBack = findViewById(R.id.btnBack);
        btnClear = findViewById(R.id.btnClear);

        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));

        // Handle Back Button (Closes screen)
        btnBack.setOnClickListener(v -> finish());

        // Handle Clear History Button (Shows Popup)
        btnClear.setOnClickListener(v -> showClearConfirmation());

        // Load Data from Server
        loadHistory();
    }

    private void showClearConfirmation() {
        // Create the popup dialog
        new AlertDialog.Builder(this).setTitle("Clear History").setMessage("Are you sure you want to clear the history ? This cannot be undone !!!").setPositiveButton("Yes, Clear All", (dialog, which) -> clearHistoryFromServer()).setNegativeButton("Cancel", null).show();
    }

    private void clearHistoryFromServer() {
        // Optional user filter support exists: ApiClient.buildClearHistoryBody(userId)
        // Current mode clears globally, so we pass null.
        RequestBody formBody = ApiClient.buildClearHistoryBody(null);

        Request request = new Request.Builder().url(ApiClient.getClearHistoryUrl()).post(formBody).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(HistoryActivity.this, "Network Error", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    // If server deleted it, reload the list
                    runOnUiThread(() -> loadHistory());
                }
            }
        });
    }

    private void loadHistory() {
        Request request = new Request.Builder().url(ApiClient.getHistoryUrl()).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(HistoryActivity.this, "Failed to load history", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    runOnUiThread(() -> {
                        try {
                            JSONArray data = new JSONArray(body);

                            // Toggle empty state
                            if (data.length() == 0) {
                                // No alerts -> "No History" text
                                txtEmpty.setVisibility(View.VISIBLE);
                                recyclerHistory.setVisibility(View.GONE);
                            } else {
                                // Have alerts -> List
                                txtEmpty.setVisibility(View.GONE);
                                recyclerHistory.setVisibility(View.VISIBLE);

                                HistoryAdapter adapter = new HistoryAdapter(HistoryActivity.this, data);
                                recyclerHistory.setAdapter(adapter);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        });
    }
}