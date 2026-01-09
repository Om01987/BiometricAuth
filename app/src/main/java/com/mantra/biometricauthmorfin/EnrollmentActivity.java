package com.mantra.biometricauthmorfin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mantra.morfinauth.MorfinAuth_Callback;
import com.mantra.morfinauth.enums.DeviceDetection;
import com.mantra.morfinauth.enums.ImageFormat;
import com.mantra.morfinauth.enums.TemplateFormat;

import java.io.File;
import java.io.FileOutputStream;

public class EnrollmentActivity extends AppCompatActivity implements MorfinAuth_Callback {

    // UI
    private TextView txtDeviceStatus, txtUserId, txtUserName, txtMessage;
    private ImageView imgFingerPreview, btnBack;
    private Button btnStartCapture, btnAutoCapture, btnStopCapture;

    // Logic
    private BiometricManager bioManager;
    private FingerprintDatabaseHelper dbHelper;
    private String storagePath;

    // State
    private boolean isCapturing = false;
    private boolean isAutoLoop = false;
    private String tempUserId, tempUserName;

    // Settings
    private int minQuality = 60;
    private int timeOut = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enrollment);

        dbHelper = new FingerprintDatabaseHelper(this);
        bioManager = BiometricManager.getInstance(this);
        storagePath = getExternalFilesDir(null).getAbsolutePath() + "/FingerData";

        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bioManager.setListener(this); // Listen for live preview

        if (bioManager.isReady()) {
            txtDeviceStatus.setText("Device Ready");
            txtDeviceStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_connected));
        } else {
            txtDeviceStatus.setText("Device Not Initialized");
            txtDeviceStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_disconnected));
            btnStartCapture.setEnabled(false);
            btnAutoCapture.setEnabled(false);
            Toast.makeText(this, "Go back and Init device first", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isCapturing) stopCapture();
        bioManager.removeListener();
    }

    private void initViews() {
        txtDeviceStatus = findViewById(R.id.txtDeviceStatus);
        txtUserId = findViewById(R.id.txtUserId);
        txtUserName = findViewById(R.id.txtUserName);
        txtMessage = findViewById(R.id.txtMessage);
        imgFingerPreview = findViewById(R.id.imgFingerPreview);
        btnBack = findViewById(R.id.btnBack);
        btnStartCapture = findViewById(R.id.btnStartCapture);
        btnAutoCapture = findViewById(R.id.btnAutoCapture);
        btnStopCapture = findViewById(R.id.btnStopCapture);

        btnBack.setOnClickListener(v -> finish());
        btnStopCapture.setOnClickListener(v -> stopCapture());

        btnStartCapture.setOnClickListener(v -> {
            isAutoLoop = false;
            showUserDialog();
        });

        btnAutoCapture.setOnClickListener(v -> {
            isAutoLoop = true;
            showUserDialog();
        });
    }

    // --- 1. USER DIALOG ---
    private void showUserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_user_input, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView txtAutoId = dialogView.findViewById(R.id.txtDialogUserId);
        EditText edtName = dialogView.findViewById(R.id.edtDialogUserName);
        Button btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
        Button btnStart = dialogView.findViewById(R.id.btnDialogStart);

        String nextId = dbHelper.getNextUserId();
        txtAutoId.setText(nextId);

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            isAutoLoop = false; // Cancel loop
        });

        btnStart.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter name", Toast.LENGTH_SHORT).show();
                return;
            }
            tempUserId = nextId;
            tempUserName = name;

            // UPDATE UI WITH USER INFO
            txtUserId.setText("User ID: " + tempUserId);
            txtUserName.setText("Name: " + tempUserName);

            dialog.dismiss();
            startCapture();
        });

        dialog.show();
    }

    // --- 2. CAPTURE LOGIC ---
    private void startCapture() {
        if (!bioManager.isReady()) return;

        isCapturing = true;
        updateButtons(true);
        // Clear preview only on fresh start, maybe keep for loop
        if (!isAutoLoop) imgFingerPreview.setImageResource(android.R.drawable.ic_menu_gallery);

        txtMessage.setText("Place finger on sensor...");

        if (isAutoLoop) {
            // Using Sync AutoCapture in background thread
            new Thread(() -> {
                int[] qty = new int[1];
                int[] nfiq = new int[1];
                int ret = bioManager.getSDK().AutoCapture(minQuality, timeOut, qty, nfiq);

                runOnUiThread(() -> {
                    if (ret == 0) {
                        txtMessage.setText("Processing Auto Capture...");
                        saveData(qty[0], nfiq[0]);
                    } else {
                        txtMessage.setText("Auto Capture Failed: " + ret);
                        isCapturing = false;
                        updateButtons(false);
                    }
                });
            }).start();
        } else {
            // Using Async StartCapture
            int ret = bioManager.getSDK().StartCapture(minQuality, timeOut);
            if (ret != 0) {
                txtMessage.setText("Start Failed: " + ret);
                isCapturing = false;
                updateButtons(false);
            }
        }
    }

    private void stopCapture() {
        if (bioManager.isReady()) {
            bioManager.getSDK().StopCapture();
        }
        isCapturing = false;
        isAutoLoop = false;
        updateButtons(false);
        txtMessage.setText("Stopped.");
    }

    private void updateButtons(boolean capturing) {
        runOnUiThread(() -> {
            btnStartCapture.setEnabled(!capturing);
            btnAutoCapture.setEnabled(!capturing);
            btnStopCapture.setEnabled(capturing);
        });
    }

    // --- 3. CALLBACKS ---
    @Override
    public void OnDeviceDetection(String deviceName, DeviceDetection detection) {
        runOnUiThread(() -> {
            if (detection == DeviceDetection.DISCONNECTED) {
                txtDeviceStatus.setText("Device Disconnected");
                txtDeviceStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_disconnected));
                stopCapture();
                finish();
                Toast.makeText(this, "Device Removed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void OnPreview(int errorCode, int quality, byte[] image) {
        if (errorCode == 0 && image != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
            runOnUiThread(() -> {
                imgFingerPreview.setImageBitmap(bitmap);
                txtMessage.setText("Quality: " + quality);
            });
        }
    }

    @Override
    public void OnComplete(int errorCode, int quality, int nfiq) {
        // Called for Async StartCapture
        runOnUiThread(() -> {
            if (errorCode == 0) {
                txtMessage.setText("Capture Success. Saving...");
                saveData(quality, nfiq);
            } else {
                txtMessage.setText("Capture Error: " + errorCode);
                isCapturing = false;
                updateButtons(false);
            }
        });
    }

    // --- 4. SAVING ---
    private void saveData(int quality, int nfiq) {
        new Thread(() -> {
            try {
                int[] size = new int[1];
                byte[] imgBuffer = new byte[800 * 800 + 1024];
                int ret1 = bioManager.getSDK().GetImage(imgBuffer, size, 1, ImageFormat.BMP);
                byte[] finalImg = new byte[size[0]];
                System.arraycopy(imgBuffer, 0, finalImg, 0, size[0]);

                byte[] tempBuffer = new byte[2048];
                int[] tSize = new int[1];
                int ret2 = bioManager.getSDK().GetTemplate(tempBuffer, tSize, TemplateFormat.FMR_V2011);
                byte[] finalTemp = new byte[tSize[0]];
                System.arraycopy(tempBuffer, 0, finalTemp, 0, tSize[0]);

                if (ret1 == 0 && ret2 == 0) {
                    // File
                    String fname = tempUserId + "_" + System.currentTimeMillis() + ".bmp";
                    File file = new File(storagePath, fname);
                    if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(finalImg);
                    fos.close();

                    // DB
                    boolean saved = dbHelper.saveFingerprint(tempUserId, tempUserName, file.getAbsolutePath(), finalTemp, quality, nfiq);

                    runOnUiThread(() -> {
                        if (saved) {
                            Toast.makeText(this, "Saved: " + tempUserName, Toast.LENGTH_SHORT).show();
                            txtMessage.setText("Enrolled " + tempUserId);

                            if (isAutoLoop) {
                                isCapturing = false;
                                showUserDialog(); // LOOP: Show dialog for next user
                            } else {
                                isCapturing = false;
                                updateButtons(false);
                            }
                        } else {
                            txtMessage.setText("DB Save Error");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override public void OnFingerPosition(int i, int i1) {}
}