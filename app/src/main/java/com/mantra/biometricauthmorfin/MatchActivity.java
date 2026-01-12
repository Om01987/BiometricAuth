package com.mantra.biometricauthmorfin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mantra.morfinauth.MorfinAuth_Callback;
import com.mantra.morfinauth.enums.DeviceDetection;
import com.mantra.morfinauth.enums.TemplateFormat;

import java.util.List;

public class MatchActivity extends AppCompatActivity implements MorfinAuth_Callback {

    private ImageView imgMatchPreview, btnBack;
    private TextView txtMatchStatus, txtResultName, txtResultScore;
    private Button btnStartMatch;

    private BiometricManager bioManager;
    private FingerprintDatabaseHelper dbHelper;

    private boolean isCapturing = false;
    private int minQuality = 60;
    private int timeOut = 10000;

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
        txtResultName = findViewById(R.id.txtResultName);
        txtResultScore = findViewById(R.id.txtResultScore);
        btnStartMatch = findViewById(R.id.btnStartMatch);

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
        txtResultName.setText("User: -");
        txtResultScore.setText("Score: 0");

        // Reset Preview
        imgMatchPreview.setImageResource(android.R.drawable.ic_menu_gallery);
        imgMatchPreview.setImageTintList(ColorStateList.valueOf(Color.LTGRAY));

        new Thread(() -> {
            bioManager.getSDK().StopCapture(); // Ensure clean state
            try { Thread.sleep(200); } catch (Exception e){}

            // Using AutoCapture for match as it gives better quality feedback
            int[] qty = new int[1];
            int[] nfiq = new int[1];

            int ret = bioManager.getSDK().AutoCapture(minQuality, timeOut, qty, nfiq);

            if(ret == 0) {
                runOnUiThread(() -> {
                    txtMatchStatus.setText("Processing...");
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
                // 1. Get Template of captured finger
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

                // 2. Fetch all users from DB
                List<FingerprintDatabaseHelper.UserRecord> users = dbHelper.getAllUsersForMatching();

                if (users.isEmpty()) {
                    runOnUiThread(() -> {
                        txtMatchStatus.setText("Database is Empty");
                        resetUI();
                    });
                    return;
                }

                // 3. Match Loop
                boolean matchFound = false;
                String matchedName = "";
                int maxScore = 0;

                for (FingerprintDatabaseHelper.UserRecord user : users) {
                    int[] score = new int[1];
                    int matchRet = bioManager.getSDK().MatchTemplate(capturedTemplate, user.template, score, TemplateFormat.FMR_V2011);

                    if (matchRet == 0 && score[0] > maxScore) {
                        maxScore = score[0];
                        // Threshold for Match (Standard is often 600+)
                        if (maxScore > 600) {
                            matchFound = true;
                            matchedName = user.userName;
                            // Break immediately on high match for speed
                            break;
                        }
                    }
                }

                // 4. Update UI
                boolean finalMatchFound = matchFound;
                String finalName = matchedName;
                int finalScore = maxScore;

                runOnUiThread(() -> {
                    if (finalMatchFound) {
                        txtMatchStatus.setText("Access Granted");
                        txtMatchStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
                        txtResultName.setText("User: " + finalName);
                        txtResultScore.setText("Score: " + finalScore);
                    } else {
                        txtMatchStatus.setText("Access Denied");
                        txtMatchStatus.setTextColor(Color.parseColor("#F44336")); // Red
                        txtResultName.setText("User: Not Found");
                        txtResultScore.setText("Best Score: " + finalScore);
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
                    imgMatchPreview.setImageTintList(null); // Remove tint
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