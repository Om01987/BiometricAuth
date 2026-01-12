package com.mantra.biometricauthmorfin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MatchResultAdapter extends RecyclerView.Adapter<MatchResultAdapter.ViewHolder> {

    private List<MatchActivity.MatchedUser> matches;

    public MatchResultAdapter(List<MatchActivity.MatchedUser> matches) {
        this.matches = matches;
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
        holder.txtScore.setText("Score: " + match.score);
    }

    @Override
    public int getItemCount() {
        return matches.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtId, txtScore;

        ViewHolder(View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtName);
            txtId = itemView.findViewById(R.id.txtId);
            txtScore = itemView.findViewById(R.id.txtScore);
        }
    }
}