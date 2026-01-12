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

    public UserAdapter(List<FingerprintDatabaseHelper.UserRecord> userList) {
        this.userList = userList;
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
        // Default to "No Name" if empty
        holder.txtUserName.setText(
                (user.userName != null && !user.userName.isEmpty()) ? user.userName : "No Name"
        );
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