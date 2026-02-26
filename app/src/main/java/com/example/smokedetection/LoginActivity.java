package com.example.smokedetection;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText inputUser, inputPass;
    private Button btnLogin, btnRegister;
    private int titleTaps = 0;
    private Toast tapToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        android.view.View root = findViewById(R.id.root);
        int pl = root.getPaddingLeft(), pt = root.getPaddingTop(),
                pr = root.getPaddingRight(), pb = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(pl + insets.left, pt + insets.top, pr + insets.right, pb + insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // Check if user is already logged in
        // If we find a saved user ID, we go straight to the main screen
        ApiClient.init(this);
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        if (prefs.getInt("user_id", -1) != -1) {
            goToMain();
            return;
        }

        // Link the variables to the items on the screen
        inputUser = findViewById(R.id.inputUser);
        inputPass = findViewById(R.id.inputPass);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        // Hidden server config: tap the title 5 times
        TextView txtTitle = findViewById(R.id.txtTitle);
        txtTitle.setOnClickListener(v -> {
            titleTaps++;
            if (titleTaps >= 5) {
                titleTaps = 0;
                showServerDialog();
            } else if (titleTaps >= 3) {
                if (tapToast != null) tapToast.cancel();
                tapToast = Toast.makeText(this, (5 - titleTaps) + " taps to server config", Toast.LENGTH_SHORT);
                tapToast.show();
            }
        });

        // Set up the button clicks
        // True means we are trying to Login
        // False means we are trying to Register
        btnLogin.setOnClickListener(v -> handleAuth(true));
        btnRegister.setOnClickListener(v -> handleAuth(false));
    }

    private void handleAuth(boolean isLogin) {
        String user = inputUser.getText().toString().trim();
        String pass = inputPass.getText().toString().trim();

        // Make sure fields are not empty
        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable buttons so the user cannot click twice
        setButtonsEnabled(false);

        // Create the instructions for what to do when the server replies
        Callback callback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // If the connection fails completely (wrong IP or server off)
                runOnUiThread(() -> {
                    setButtonsEnabled(true);
                    Toast.makeText(LoginActivity.this, "Connection Error: Check IP!", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String body = response.body().string();

                // Run this part on the main UI thread to update the screen
                runOnUiThread(() -> {
                    setButtonsEnabled(true);
                    try {
                        JSONObject json = new JSONObject(body);

                        // Check if the server said "success"
                        if (json.getString("status").equals("success")) {

                            // Save the User ID to the phone memory so we stay logged in
                            int userId = json.getInt("user_id");
                            getSharedPreferences("AppPrefs", MODE_PRIVATE)
                                    .edit().putInt("user_id", userId).apply();

                            Toast.makeText(LoginActivity.this, "Success!", Toast.LENGTH_SHORT).show();
                            goToMain();

                        } else {
                            // If login failed, show the error message from the server
                            String msg = json.optString("message", "Auth failed");
                            Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(LoginActivity.this, "Server Error: " + body, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        };

        // Send the data to the correct helper function
        if (isLogin) {
            ApiClient.login(user, pass, callback);
        } else {
            ApiClient.register(user, pass, callback);
        }
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void setButtonsEnabled(boolean enable) {
        btnLogin.setEnabled(enable);
        btnRegister.setEnabled(enable);
    }

    private void showServerDialog() {
        EditText input = new EditText(this);
        input.setText(ApiClient.getBaseUrl());
        input.setSelectAllOnFocus(true);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);

        new AlertDialog.Builder(this)
                .setTitle("Server")
                .setMessage("http://<ip>:<port>")
                .setView(input, pad, 0, pad, 0)
                .setPositiveButton("Test & Save", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (url.isEmpty()) return;

                    // Temporarily set so ping() hits the new URL
                    ApiClient.setServer(this, url);

                    ApiClient.ping(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            runOnUiThread(() ->
                                    Toast.makeText(LoginActivity.this,
                                            "Unreachable: " + e.getMessage(), Toast.LENGTH_LONG).show()
                            );
                        }

                        @Override
                        public void onResponse(Call call, Response response) {
                            boolean ok = response.isSuccessful();
                            runOnUiThread(() -> {
                                if (ok) {
                                    Toast.makeText(LoginActivity.this,
                                            "Connected to " + url, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(LoginActivity.this,
                                            "Server returned " + response.code(), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}