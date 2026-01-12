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
    private boolean stopRequested = false;
    private boolean isAutoCaptureMode = false; // true = Sync/Thread loop, false = Async/Callback loop

    private String tempUserId, tempUserName;
    private int captureCount = 0;
    private static final int MAX_FINGERS = 10;

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
        bioManager.setListener(this);

        if (bioManager.isReady()) {
            txtDeviceStatus.setText("Device Ready");
            txtDeviceStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_connected));
            // Show Next ID immediately
            fetchNextId();
        } else {
            txtDeviceStatus.setText("Device Not Initialized");
            txtDeviceStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_disconnected));
            btnStartCapture.setEnabled(false);
            btnAutoCapture.setEnabled(false);
            Toast.makeText(this, "Go back and Init device first", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchNextId() {
        String nextId = dbHelper.getNextUserId();
        txtUserId.setText("User ID: " + nextId);
        tempUserId = nextId; // Store for later
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
            isAutoCaptureMode = false;
            showUserDialog();
        });

        btnAutoCapture.setOnClickListener(v -> {
            isAutoCaptureMode = true;
            showUserDialog();
        });
    }

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

        txtAutoId.setText(tempUserId); // Use fetched ID

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnStart.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter name", Toast.LENGTH_SHORT).show();
                return;
            }
            tempUserName = name;
            txtUserName.setText("Name: " + tempUserName);
            dialog.dismiss();

            // Reset counters before starting
            captureCount = 0;
            stopRequested = false;
            isCapturing = true;
            updateButtons(true);

            if (isAutoCaptureMode) {
                runAutoCaptureLoop();
            } else {
                runAsyncCaptureLoop();
            }
        });

        dialog.show();
    }

    // --- MODE 1: ASYNC LOOP (StartCapture) ---
    private void runAsyncCaptureLoop() {
        if (stopRequested || captureCount >= MAX_FINGERS) {
            finishSession("Capture Complete.");
            return;
        }

        txtMessage.setText("Finger " + (captureCount + 1) + "/" + MAX_FINGERS + ": Place Finger...");

        // This is non-blocking. Result comes in OnComplete
        int ret = bioManager.getSDK().StartCapture(minQuality, timeOut);
        if (ret != 0) {
            txtMessage.setText("Start Failed: " + ret);
            isCapturing = false;
            updateButtons(false);
        }
    }

    // --- MODE 2: SYNC LOOP (AutoCapture) ---
    private void runAutoCaptureLoop() {
        new Thread(() -> {
            while (captureCount < MAX_FINGERS && !stopRequested) {
                runOnUiThread(() -> txtMessage.setText("Finger " + (captureCount + 1) + ": Place Finger (Auto)..."));

                int[] qty = new int[1];
                int[] nfiq = new int[1];

                // Blocks here until finger detected or timeout
                int ret = bioManager.getSDK().AutoCapture(minQuality, timeOut, qty, nfiq);

                if (ret == 0) {
                    captureCount++;
                    saveData(qty[0], nfiq[0]); // Save
                    // Wait a bit so user lifts finger
                    try { Thread.sleep(1000); } catch (InterruptedException e) {}
                } else {
                    if (ret == -2019) continue; // Timeout, retry
                    // Error
                    runOnUiThread(() -> txtMessage.setText("Auto Error: " + ret));
                    break;
                }
            }
            runOnUiThread(() -> finishSession("Auto Loop Finished"));
        }).start();
    }

    private void stopCapture() {
        stopRequested = true;
        if (bioManager.isReady()) {
            bioManager.getSDK().StopCapture();
        }
        txtMessage.setText("Stopping...");
    }

    private void finishSession(String msg) {
        isCapturing = false;
        stopRequested = false;
        updateButtons(false);
        txtMessage.setText(msg);
        fetchNextId(); // Prepare ID for next user
    }

    private void updateButtons(boolean capturing) {
        runOnUiThread(() -> {
            btnStartCapture.setEnabled(!capturing);
            btnAutoCapture.setEnabled(!capturing);
            btnStopCapture.setEnabled(capturing);
        });
    }

    // --- CALLBACKS ---
    @Override
    public void OnDeviceDetection(String deviceName, DeviceDetection detection) {
        runOnUiThread(() -> {
            if (detection == DeviceDetection.DISCONNECTED) {
                txtDeviceStatus.setText("Device Disconnected");
                txtDeviceStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_disconnected));
                stopCapture();
                finish();
            }
        });
    }

    @Override
    public void OnPreview(int errorCode, int quality, byte[] image) {
        if (errorCode == 0 && image != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
            runOnUiThread(() -> {
                imgFingerPreview.setImageBitmap(bitmap);
                // Don't overwrite message if we are in loop info mode
            });
        }
    }

    @Override
    public void OnComplete(int errorCode, int quality, int nfiq) {
        // Only for Async Mode
        if (isAutoCaptureMode) return;

        runOnUiThread(() -> {
            if (errorCode == 0) {
                captureCount++;
                saveData(quality, nfiq);

                // Trigger next capture if not stopped
                if (!stopRequested && captureCount < MAX_FINGERS) {
                    // Small delay to allow lifting finger
                    new android.os.Handler().postDelayed(this::runAsyncCaptureLoop, 1000);
                } else {
                    finishSession("Capture Session Ended.");
                }
            } else {
                txtMessage.setText("Error: " + errorCode);
                // Retry?
                if (!stopRequested) runAsyncCaptureLoop();
            }
        });
    }

    // --- SAVING ---
    private void saveData(int quality, int nfiq) {
        // Note: In AutoMode this runs on BG thread. In Async, on Main.
        // We put it in a thread to be safe.
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
                    String fname = tempUserId + "_" + captureCount + "_" + System.currentTimeMillis() + ".bmp";
                    File file = new File(storagePath, fname);
                    if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(finalImg);
                    fos.close();

                    dbHelper.saveFingerprint(tempUserId, tempUserName, file.getAbsolutePath(), finalTemp, quality, nfiq);

                    runOnUiThread(() ->
                            Toast.makeText(this, "Saved Finger " + captureCount, Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override public void OnFingerPosition(int i, int i1) {}
}