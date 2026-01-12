package com.mantra.biometricauthmorfin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private List<FingerprintDatabaseHelper.UserRecord> userList;
    private OnUserLongClickListener longClickListener;

    public interface OnUserLongClickListener {
        void onUserLongClick(FingerprintDatabaseHelper.UserRecord user);
    }

    public UserAdapter(List<FingerprintDatabaseHelper.UserRecord> userList, OnUserLongClickListener listener) {
        this.userList = userList;
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        FingerprintDatabaseHelper.UserRecord user = userList.get(position);
        holder.txtUserId.setText(user.userId);
        holder.txtUserName.setText((user.userName != null && !user.userName.isEmpty()) ? user.userName : "No Name");

        // Long click listener
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onUserLongClick(user);
            }
            return true; // Consume event
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView txtUserId, txtUserName;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            txtUserId = itemView.findViewById(R.id.txtUserId);
            txtUserName = itemView.findViewById(R.id.txtUserName);
        }
    }
}