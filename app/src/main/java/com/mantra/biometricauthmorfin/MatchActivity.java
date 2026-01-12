package com.mantra.biometricauthmorfin;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mantra.morfinauth.MorfinAuth_Callback;
import com.mantra.morfinauth.enums.DeviceDetection;
import com.mantra.morfinauth.enums.TemplateFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MatchActivity extends AppCompatActivity implements MorfinAuth_Callback {

    private ImageView imgMatchPreview, btnBack;
    private TextView txtMatchStatus;
    private Button btnStartMatch;
    private RecyclerView recyclerMatches;

    private BiometricManager bioManager;
    private FingerprintDatabaseHelper dbHelper;

    private boolean isCapturing = false;
    private int minQuality = 60;
    private int timeOut = 10000;

    // Model class to hold match results
    public static class MatchedUser implements Comparable<MatchedUser> {
        String name;
        String id;
        int score;

        public MatchedUser(String name, String id, int score) {
            this.name = name;
            this.id = id;
            this.score = score;
        }

        @Override
        public int compareTo(MatchedUser o) {
            // Sort Descending by Score
            return Integer.compare(o.score, this.score);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match);

        bioManager = BiometricManager.getInstance(this);
        dbHelper = new FingerprintDatabaseHelper(this);

        initViews();
    }

    private void initViews() {
        imgMatchPreview = findViewById(R.id.imgMatchPreview);
        btnBack = findViewById(R.id.btnBack);
        txtMatchStatus = findViewById(R.id.txtMatchStatus);
        btnStartMatch = findViewById(R.id.btnStartMatch);
        recyclerMatches = findViewById(R.id.recyclerMatches);

        recyclerMatches.setLayoutManager(new LinearLayoutManager(this));

        btnBack.setOnClickListener(v -> finish());

        btnStartMatch.setOnClickListener(v -> {
            if(!isCapturing) startCapture();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bioManager.setListener(this);
        if(!bioManager.isReady()) {
            Toast.makeText(this, "Device not ready", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCapture();
        bioManager.removeListener();
    }

    private void startCapture() {
        isCapturing = true;
        btnStartMatch.setEnabled(false);
        txtMatchStatus.setText("Place Finger...");

        // Clear previous results
        recyclerMatches.setAdapter(null);

        // Reset Preview
        imgMatchPreview.setImageResource(android.R.drawable.ic_menu_gallery);
        imgMatchPreview.setImageTintList(ColorStateList.valueOf(Color.LTGRAY));

        new Thread(() -> {
            bioManager.getSDK().StopCapture();
            try { Thread.sleep(200); } catch (Exception e){}

            int[] qty = new int[1];
            int[] nfiq = new int[1];

            int ret = bioManager.getSDK().AutoCapture(minQuality, timeOut, qty, nfiq);

            if(ret == 0) {
                runOnUiThread(() -> {
                    txtMatchStatus.setText("Processing Matches...");
                    processMatch(qty[0]);
                });
            } else {
                runOnUiThread(() -> {
                    txtMatchStatus.setText("Capture Failed: " + ret);
                    resetUI();
                });
            }
        }).start();
    }

    private void stopCapture() {
        if(isCapturing) {
            new Thread(() -> bioManager.getSDK().StopCapture()).start();
            isCapturing = false;
        }
    }

    private void processMatch(int quality) {
        new Thread(() -> {
            try {
                // 1. Get Template
                byte[] tempBuffer = new byte[2048];
                int[] tSize = new int[1];
                int ret = bioManager.getSDK().GetTemplate(tempBuffer, tSize, TemplateFormat.FMR_V2011);

                if (ret != 0) {
                    runOnUiThread(() -> {
                        txtMatchStatus.setText("Template Extraction Failed");
                        resetUI();
                    });
                    return;
                }

                byte[] capturedTemplate = new byte[tSize[0]];
                System.arraycopy(tempBuffer, 0, capturedTemplate, 0, tSize[0]);

                // 2. Fetch Users
                List<FingerprintDatabaseHelper.UserRecord> users = dbHelper.getAllUsersForMatching();

                if (users.isEmpty()) {
                    runOnUiThread(() -> {
                        txtMatchStatus.setText("Database is Empty");
                        resetUI();
                    });
                    return;
                }

                // 3. Match ALL Users
                List<MatchedUser> foundMatches = new ArrayList<>();
                int threshold = 400; // --- UPDATED THRESHOLD TO 400 ---

                for (FingerprintDatabaseHelper.UserRecord user : users) {
                    int[] score = new int[1];
                    int matchRet = bioManager.getSDK().MatchTemplate(capturedTemplate, user.template, score, TemplateFormat.FMR_V2011);

                    if (matchRet == 0 && score[0] > threshold) {
                        foundMatches.add(new MatchedUser(user.userName, user.userId, score[0]));
                    }
                }

                // 4. Sort results (Best match first)
                Collections.sort(foundMatches);

                // 5. Update UI
                runOnUiThread(() -> {
                    if (!foundMatches.isEmpty()) {
                        txtMatchStatus.setText("Found " + foundMatches.size() + " Matches");
                        txtMatchStatus.setTextColor(Color.parseColor("#4CAF50"));

                        MatchResultAdapter adapter = new MatchResultAdapter(foundMatches);
                        recyclerMatches.setAdapter(adapter);
                    } else {
                        txtMatchStatus.setText("No Matches Found");
                        txtMatchStatus.setTextColor(Color.parseColor("#F44336"));
                    }
                    resetUI();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(this::resetUI);
            }
        }).start();
    }

    private void resetUI() {
        isCapturing = false;
        btnStartMatch.setEnabled(true);
    }

    @Override
    public void OnPreview(int errorCode, int quality, byte[] image) {
        if (errorCode == 0 && image != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
            runOnUiThread(() -> {
                if (bitmap != null) {
                    imgMatchPreview.setImageTintList(null);
                    imgMatchPreview.clearColorFilter();
                    imgMatchPreview.setImageBitmap(bitmap);
                }
            });
        }
    }

    @Override public void OnDeviceDetection(String s, DeviceDetection d) {
        if(d == DeviceDetection.DISCONNECTED) finish();
    }
    @Override public void OnComplete(int i, int i1, int i2) {}
    @Override public void OnFingerPosition(int i, int i1) {}
}