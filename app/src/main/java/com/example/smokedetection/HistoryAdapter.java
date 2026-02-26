package com.example.smokedetection;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private JSONArray data;
    private Context context;

    public HistoryAdapter(Context context, JSONArray data) {
        this.context = context;
        this.data = data;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject item = data.getJSONObject(position);

            // Set Text
            holder.txtTimestamp.setText(item.getString("timestamp"));
            double conf = item.getDouble("confidence") * 100;
            holder.txtConfidence.setText(String.format("Confidence: %.1f%%", conf));

            // Load Image from Python Server
            // Add the Base URL: "http://192...:8000/static/alerts/..."
            String fullUrl = ApiClient.getBaseUrl() + item.getString("image_path");

            Glide.with(context)
                    .load(fullUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(holder.imgEvidence);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, ViewEvidenceActivity.class);
                // Pass the image URL to the next screen so it knows what to show
                intent.putExtra("IMAGE_URL", fullUrl);
                context.startActivity(intent);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return data.length();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtTimestamp, txtConfidence;
        ImageView imgEvidence;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTimestamp = itemView.findViewById(R.id.txtTimestamp);
            txtConfidence = itemView.findViewById(R.id.txtConfidence);
            imgEvidence = itemView.findViewById(R.id.imgEvidence);
        }
    }
}