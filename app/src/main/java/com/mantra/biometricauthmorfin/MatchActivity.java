package com.mantra.biometricauthmorfin;

import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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

    private List<MatchedUser> currentMatches = new ArrayList<>();

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
            if (!isCapturing) startCapture();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bioManager.setListener(this);
        if (!bioManager.isReady()) {
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

        currentMatches.clear();
        recyclerMatches.setAdapter(null);

        imgMatchPreview.setImageResource(android.R.drawable.ic_menu_gallery);
        imgMatchPreview.setImageTintList(ColorStateList.valueOf(Color.LTGRAY));

        new Thread(() -> {
            bioManager.getSDK().StopCapture();
            try { Thread.sleep(200); } catch (Exception e){}

            int[] qty = new int[1];
            int[] nfiq = new int[1];

            int ret = bioManager.getSDK().AutoCapture(minQuality, timeOut, qty, nfiq);

            if (ret == 0) {
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
        if (isCapturing) {
            new Thread(() -> bioManager.getSDK().StopCapture()).start();
            isCapturing = false;
        }
    }

    private void processMatch(int quality) {
        new Thread(() -> {
            Cursor cursor = null;
            try {

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


                cursor = dbHelper.getUsersCursor();

                if (cursor == null || cursor.getCount() == 0) {
                    runOnUiThread(() -> {
                        txtMatchStatus.setText("Database is Empty");
                        resetUI();
                    });
                    return;
                }

                List<MatchedUser> foundMatches = new ArrayList<>();
                int threshold = 400;


                int idxId = cursor.getColumnIndex(FingerprintDatabaseHelper.COL_USER_ID);
                int idxName = cursor.getColumnIndex(FingerprintDatabaseHelper.COL_USER_NAME);
                int idxTemplate = cursor.getColumnIndex(FingerprintDatabaseHelper.COL_TEMPLATE);


                while (cursor.moveToNext()) {
                    String userId = cursor.getString(idxId);
                    String userName = cursor.getString(idxName);
                    byte[] dbTemplate = cursor.getBlob(idxTemplate);

                    int[] score = new int[1];
                    int matchRet = bioManager.getSDK().MatchTemplate(capturedTemplate, dbTemplate, score, TemplateFormat.FMR_V2011);

                    if (matchRet == 0 && score[0] > threshold) {
                        foundMatches.add(new MatchedUser(userName, userId, score[0]));
                    }
                }

                Collections.sort(foundMatches);
                currentMatches = foundMatches;

                runOnUiThread(() -> {
                    if (!currentMatches.isEmpty()) {
                        txtMatchStatus.setText("Found " + currentMatches.size() + " Matches");
                        txtMatchStatus.setTextColor(Color.parseColor("#4CAF50"));

                        MatchResultAdapter adapter = new MatchResultAdapter(currentMatches, this::showDeleteDialog);
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
            } finally {

                if (cursor != null) {
                    cursor.close();
                }
            }
        }).start();
    }

    private void showDeleteDialog(MatchedUser user) {
        new AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("Do you want to delete " + user.name + " (" + user.id + ")?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean deleted = dbHelper.deleteUser(user.id);
                    if (deleted) {
                        Toast.makeText(this, "Deleted: " + user.name, Toast.LENGTH_SHORT).show();

                        currentMatches.remove(user);

                        if (recyclerMatches.getAdapter() != null) {
                            recyclerMatches.getAdapter().notifyDataSetChanged();
                        }

                        if (currentMatches.isEmpty()) {
                            txtMatchStatus.setText("No Matches Remaining");
                            txtMatchStatus.setTextColor(Color.parseColor("#F44336"));
                        } else {
                            txtMatchStatus.setText("Found " + currentMatches.size() + " Matches");
                            txtMatchStatus.setTextColor(Color.parseColor("#4CAF50"));
                        }

                    } else {
                        Toast.makeText(this, "Delete Failed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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

    @Override
    public void OnDeviceDetection(String s, DeviceDetection d) {
        if (d == DeviceDetection.DISCONNECTED) {
            finish();
            Toast.makeText(this, "Device Disconnected", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void OnComplete(int i, int i1, int i2) {}
    @Override public void OnFingerPosition(int i, int i1) {}
}