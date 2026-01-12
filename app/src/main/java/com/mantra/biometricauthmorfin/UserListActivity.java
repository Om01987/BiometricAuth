package com.mantra.biometricauthmorfin;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class UserListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView txtEmpty, txtCount;
    private FingerprintDatabaseHelper dbHelper;

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

        if (users != null && !users.isEmpty()) {
            UserAdapter adapter = new UserAdapter(users);
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
}