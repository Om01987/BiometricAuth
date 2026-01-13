package com.mantra.biometricauthmorfin;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
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
            adapter = new UserAdapter(users, new UserAdapter.OnUserActionListener() {
                @Override
                public void onUserLongClick(FingerprintDatabaseHelper.UserRecord user) {
                    showDeleteDialog(user);
                }

                @Override
                public void onUserEditClick(FingerprintDatabaseHelper.UserRecord user) {
                    showEditDialog(user);
                }
            });

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

    // --- RENAME DIALOG ---
    private void showEditDialog(FingerprintDatabaseHelper.UserRecord user) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(user.userName);
        input.setSelection(input.getText().length()); // Cursor at end

        // Add some padding to the input view
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Rename User")
                .setMessage("Enter new name for " + user.userId)
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        boolean updated = dbHelper.updateUserName(user.userId, newName);
                        if (updated) {
                            Toast.makeText(this, "Name Updated", Toast.LENGTH_SHORT).show();
                            loadData(); // Refresh List
                        } else {
                            Toast.makeText(this, "Update Failed", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- DELETE DIALOG ---
    private void showDeleteDialog(FingerprintDatabaseHelper.UserRecord user) {
        new AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete " + user.userName + " (" + user.userId + ")? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean deleted = dbHelper.deleteUser(user.userId);
                    if (deleted) {
                        Toast.makeText(this, "User Deleted", Toast.LENGTH_SHORT).show();
                        loadData();
                    } else {
                        Toast.makeText(this, "Delete Failed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}