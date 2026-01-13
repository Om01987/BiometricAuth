package com.mantra.biometricauthmorfin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private List<FingerprintDatabaseHelper.UserRecord> userList;
    private OnUserActionListener actionListener;

    public interface OnUserActionListener {
        void onUserLongClick(FingerprintDatabaseHelper.UserRecord user); // For Delete
        void onUserEditClick(FingerprintDatabaseHelper.UserRecord user); // For Rename
    }

    public UserAdapter(List<FingerprintDatabaseHelper.UserRecord> userList, OnUserActionListener listener) {
        this.userList = userList;
        this.actionListener = listener;
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

        // 1. Long Press on Card -> Delete
        holder.itemView.setOnLongClickListener(v -> {
            if (actionListener != null) {
                actionListener.onUserLongClick(user);
            }
            return true;
        });

        // 2. Click on Edit Icon -> Rename
        holder.btnEdit.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onUserEditClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView txtUserId, txtUserName;
        ImageView btnEdit;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            txtUserId = itemView.findViewById(R.id.txtUserId);
            txtUserName = itemView.findViewById(R.id.txtUserName);
            btnEdit = itemView.findViewById(R.id.btnEditUser);
        }
    }
}