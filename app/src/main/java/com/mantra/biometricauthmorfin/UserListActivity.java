package com.mantra.biometricauthmorfin;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class UserListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView txtEmpty, txtCount;
    private FingerprintDatabaseHelper dbHelper;
    private UserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_list);

        dbHelper = new FingerprintDatabaseHelper(this);
        initViews();
        loadData();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        txtEmpty = findViewById(R.id.txtEmpty);
        txtCount = findViewById(R.id.txtCount);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadData() {
        List<FingerprintDatabaseHelper.UserRecord> users = dbHelper.getAllUsersList();
        updateUI(users);
    }

    private void updateUI(List<FingerprintDatabaseHelper.UserRecord> users) {
        if (users != null && !users.isEmpty()) {
            // Pass the long click listener here
            adapter = new UserAdapter(users, this::showDeleteDialog);
            recyclerView.setAdapter(adapter);
            txtEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            txtCount.setText("(" + users.size() + ")");
        } else {
            txtEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            txtCount.setText("(0)");
        }
    }

    private void showDeleteDialog(FingerprintDatabaseHelper.UserRecord user) {
        new AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete " + user.userName + " (" + user.userId + ")? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean deleted = dbHelper.deleteUser(user.userId);
                    if (deleted) {
                        Toast.makeText(this, "User Deleted", Toast.LENGTH_SHORT).show();
                        loadData(); // Refresh list
                    } else {
                        Toast.makeText(this, "Delete Failed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}