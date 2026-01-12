package com.mantra.biometricauthmorfin;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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

    private TextView txtDeviceStatus, txtUserId, txtUserName, txtMessage;
    private ImageView imgFingerPreview, btnBack;
    private Button btnStartCapture, btnAutoCapture, btnStopCapture;

    private BiometricManager bioManager;
    private FingerprintDatabaseHelper dbHelper;
    private String storagePath;

    private boolean isCapturing = false;
    private volatile boolean stopRequested = false;
    private boolean isAutoCaptureMode = false;

    private String tempUserId, tempUserName;
    private int captureCount = 0;
    private static final int MAX_FINGERS = 10;

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
        tempUserId = nextId;
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCaptureProcess();
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
        btnStopCapture.setOnClickListener(v -> stopCaptureProcess());

        btnStartCapture.setOnClickListener(v -> {
            isAutoCaptureMode = false;
            captureCount = 0;
            stopRequested = false;
            showUserDialog();
        });

        btnAutoCapture.setOnClickListener(v -> {
            isAutoCaptureMode = true;
            captureCount = 0;
            stopRequested = false;
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
        dialog.setCancelable(false);

        TextView txtAutoId = dialogView.findViewById(R.id.txtDialogUserId);
        EditText edtName = dialogView.findViewById(R.id.edtDialogUserName);
        Button btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
        Button btnStart = dialogView.findViewById(R.id.btnDialogStart);

        txtAutoId.setText(tempUserId);

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            stopRequested = true;
            updateButtons(false);
            txtMessage.setText("Enrollment Cancelled");
        });

        btnStart.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter name", Toast.LENGTH_SHORT).show();
                return;
            }
            tempUserName = name;
            txtUserName.setText("Name: " + tempUserName);
            txtUserId.setText("User ID: " + tempUserId);

            stopRequested = false;
            dialog.dismiss();
            prepareAndStartCapture();
        });

        dialog.show();
    }

    private void prepareAndStartCapture() {
        if (!bioManager.isReady()) return;

        isCapturing = true;
        updateButtons(true);


        imgFingerPreview.setImageResource(android.R.drawable.ic_menu_gallery);

        imgFingerPreview.setImageTintList(ColorStateList.valueOf(Color.LTGRAY));


        txtMessage.setText("Place finger on sensor...");

        new Thread(() -> {

            bioManager.getSDK().StopCapture();
            try { Thread.sleep(200); } catch (Exception e){}

            runOnUiThread(() -> {
                if (isAutoCaptureMode) {
                    runSyncAutoCapture();
                } else {
                    runAsyncStartCapture();
                }
            });
        }).start();
    }

    private void runAsyncStartCapture() {
        if (stopRequested) return;

        int ret = bioManager.getSDK().StartCapture(minQuality, timeOut);

        if (ret != 0) {
            txtMessage.setText("Start Failed: " + ret);
            isCapturing = false;
            updateButtons(false);
        }
    }

    private void runSyncAutoCapture() {
        new Thread(() -> {
            int[] qty = new int[1];
            int[] nfiq = new int[1];

            while (!stopRequested) {
                int ret = bioManager.getSDK().AutoCapture(minQuality, timeOut, qty, nfiq);

                if (ret == 0) {
                    runOnUiThread(() -> {
                        txtMessage.setText("Processing Capture...");
                        saveData(qty[0], nfiq[0]);
                    });
                    break;
                } else if (ret == -2019) {
                    runOnUiThread(() -> txtMessage.setText("Timeout. Place finger..."));
                    continue;
                } else {
                    runOnUiThread(() -> {
                        txtMessage.setText("Auto Capture Error: " + ret);
                        isCapturing = false;
                        updateButtons(false);
                    });
                    break;
                }
            }
        }).start();
    }

    private void stopCaptureProcess() {
        stopRequested = true;
        if (bioManager.isReady()) {
            new Thread(() -> bioManager.getSDK().StopCapture()).start();
        }
        isCapturing = false;
        updateButtons(false);
        txtMessage.setText("Stopped.");
        txtMessage.setText("idle.");
    }

    private void updateButtons(boolean capturing) {
        runOnUiThread(() -> {
            btnStartCapture.setEnabled(!capturing);
            btnAutoCapture.setEnabled(!capturing);
            btnStopCapture.setEnabled(capturing);
        });
    }

    @Override
    public void OnDeviceDetection(String deviceName, DeviceDetection detection) {
        runOnUiThread(() -> {
            if (detection == DeviceDetection.DISCONNECTED) {
                txtDeviceStatus.setText("Device Disconnected");
                txtDeviceStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.status_disconnected));
                stopCaptureProcess();
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
                if (bitmap != null) {

                    imgFingerPreview.setImageTintList(null);

                    imgFingerPreview.clearColorFilter();


                    imgFingerPreview.setImageBitmap(bitmap);
                    txtMessage.setText("Quality: " + quality);
                }
            });
        }
    }


    @Override
    public void OnComplete(int errorCode, int quality, int nfiq) {
        if (isAutoCaptureMode) return;

        runOnUiThread(() -> {
            if (errorCode == 0) {
                txtMessage.setText("Capture Success. Saving...");
                saveData(quality, nfiq);
            } else if (errorCode == -2019) {
                if (!stopRequested) {
                    txtMessage.setText("Timeout. Retrying...");
                    runAsyncStartCapture();
                }
            } else {
                txtMessage.setText("Capture Error: " + errorCode);
                isCapturing = false;
                updateButtons(false);
            }
        });
    }

    private void saveData(int quality, int nfiq) {
        new Thread(() -> {
            try {
                int[] size = new int[1];

                byte[] imgBuffer = new byte[800 * 800 + 2048];
                int ret1 = bioManager.getSDK().GetImage(imgBuffer, size, 1, ImageFormat.BMP);

                if(ret1 == 0) {
                    byte[] finalImg = new byte[size[0]];
                    System.arraycopy(imgBuffer, 0, finalImg, 0, size[0]);

                    byte[] tempBuffer = new byte[2048];
                    int[] tSize = new int[1];
                    int ret2 = bioManager.getSDK().GetTemplate(tempBuffer, tSize, TemplateFormat.FMR_V2011);

                    if (ret2 == 0) {
                        byte[] finalTemp = new byte[tSize[0]];
                        System.arraycopy(tempBuffer, 0, finalTemp, 0, tSize[0]);

                        String fname = tempUserId + "_" + System.currentTimeMillis() + ".bmp";
                        File file = new File(storagePath, fname);
                        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
                        FileOutputStream fos = new FileOutputStream(file);
                        fos.write(finalImg);
                        fos.close();

                        boolean saved = dbHelper.saveFingerprint(tempUserId, tempUserName, file.getAbsolutePath(), finalTemp, quality, nfiq);

                        runOnUiThread(() -> {
                            if (saved) {
                                Toast.makeText(this, "Enrolled: " + tempUserName, Toast.LENGTH_SHORT).show();
                                onSaveSuccess();
                            } else {
                                txtMessage.setText("DB Save Error");
                                isCapturing = false;
                                updateButtons(false);
                            }
                        });
                    } else {
                        runOnUiThread(() -> txtMessage.setText("Template Error: " + ret2));
                    }
                } else {
                    runOnUiThread(() -> txtMessage.setText("GetImage Error: " + ret1));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void onSaveSuccess() {
        captureCount++;
        if (captureCount < MAX_FINGERS && !stopRequested) {
            fetchNextId();
            txtUserName.setText("Name: Waiting...");
            txtMessage.setText("User Saved. Next...");


            imgFingerPreview.setImageResource(android.R.drawable.ic_menu_gallery);
            imgFingerPreview.setImageTintList(ColorStateList.valueOf(Color.LTGRAY));

            showUserDialog();
        } else {
            finishSession("Session Complete (" + captureCount + " Users)");
        }
    }

    private void finishSession(String msg) {
        isCapturing = false;
        stopRequested = false;
        updateButtons(false);
        txtMessage.setText(msg);
        fetchNextId();

        imgFingerPreview.setImageResource(android.R.drawable.ic_menu_gallery);
        imgFingerPreview.setImageTintList(ColorStateList.valueOf(Color.LTGRAY));
    }

    @Override public void OnFingerPosition(int i, int i1) {}
}