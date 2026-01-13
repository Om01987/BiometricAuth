package com.mantra.biometricauthmorfin;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MatchResultAdapter extends RecyclerView.Adapter<MatchResultAdapter.ViewHolder> {

    private List<MatchActivity.MatchedUser> matches;
    private OnMatchLongClickListener listener;

    public interface OnMatchLongClickListener {
        void onMatchLongClick(MatchActivity.MatchedUser user);
    }

    public MatchResultAdapter(List<MatchActivity.MatchedUser> matches, OnMatchLongClickListener listener) {
        this.matches = matches;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_match_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MatchActivity.MatchedUser match = matches.get(position);
        holder.txtName.setText(match.name);
        holder.txtId.setText(match.id);

        // --- NEW LOGIC: Vertical Stack ---
        int percentage = match.score / 10; // 0-100%

        holder.txtPercentage.setText(percentage + "%");
        holder.txtScore.setText("Score: " + match.score);
        holder.progressScore.setProgress(match.score);

        // Color Logic: High Match (>600) = Green, Else = Orange
        int color;
        if (match.score >= 600) {
            color = Color.parseColor("#4CAF50"); // Green
        } else {
            color = Color.parseColor("#FF9800"); // Orange
        }

        holder.txtPercentage.setTextColor(color);
        holder.progressScore.setProgressTintList(ColorStateList.valueOf(color));
        // ---------------------------------

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onMatchLongClick(match);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return matches.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtId, txtScore, txtPercentage; // Added txtPercentage
        ProgressBar progressScore;

        ViewHolder(View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtName);
            txtId = itemView.findViewById(R.id.txtId);
            txtPercentage = itemView.findViewById(R.id.txtPercentage); // Bind new view
            txtScore = itemView.findViewById(R.id.txtScore);
            progressScore = itemView.findViewById(R.id.progressScore);
        }
    }
}