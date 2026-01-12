package com.mantra.biometricauthmorfin;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mantra.morfinauth.DeviceInfo;
import com.mantra.morfinauth.MorfinAuth_Callback;
import com.mantra.morfinauth.enums.DeviceDetection;
import com.mantra.morfinauth.enums.DeviceModel;

public class MainActivity extends AppCompatActivity implements MorfinAuth_Callback {

    private CardView cardCapture, cardMatch;
    private TextView txtConnectionStatus, txtDeviceDetails, txtTotalUsers, txtBottomMessage;
    private ImageView imgConnStatus, imgHeaderStatus;
    private Button btnInitDevice, btnUninitDevice, btnShowUsers, btnDeleteOptions;

    private BiometricManager bioManager;
    private FingerprintDatabaseHelper dbHelper;
    private DeviceModel pendingModel;
    private int selectedDeleteOption = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        dbHelper = new FingerprintDatabaseHelper(this);
        bioManager = BiometricManager.getInstance(this);

        initViews();
        setupListeners();
        setUIConnected(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bioManager.setListener(this);
        refreshUserCount();


        if (bioManager.isConnected()) {

            DeviceModel model = bioManager.getConnectedModel();
            pendingModel = model;
            setUIConnected(true);
            txtConnectionStatus.setText("Connected (" + (model != null ? model.name() : "Device") + ")");

            if (bioManager.isReady()) {

                DeviceInfo info = bioManager.getLastDeviceInfo();
                if (info != null) {
                    fillDeviceDetails(info);
                }
                txtBottomMessage.setText("System Ready. Select an action.");
                btnInitDevice.setEnabled(false);
                btnUninitDevice.setEnabled(true);
            } else {

                txtDeviceDetails.setText("Make: -\nModel: -\nSerial: -\nW/H: -");
                txtBottomMessage.setText("Device found! Press INIT.");
                btnInitDevice.setEnabled(true);
                btnUninitDevice.setEnabled(false);
            }
        } else {

            pendingModel = null;
            setUIConnected(false);
            txtConnectionStatus.setText("Disconnected");
            txtDeviceDetails.setText("Make: -\nModel: -\nSerial: -\nW/H: -");
            txtBottomMessage.setText("Please connect a supported device.");
            btnInitDevice.setEnabled(false);
            btnUninitDevice.setEnabled(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        bioManager.removeListener();
    }

    private void initViews() {
        cardCapture = findViewById(R.id.cardCapture);
        cardMatch = findViewById(R.id.cardMatch);
        txtConnectionStatus = findViewById(R.id.txtConnectionStatus);
        txtDeviceDetails = findViewById(R.id.txtDeviceDetails);
        txtTotalUsers = findViewById(R.id.txtTotalUsers);
        txtBottomMessage = findViewById(R.id.txtBottomMessage);

        imgConnStatus = findViewById(R.id.imgConnStatus);
        imgHeaderStatus = findViewById(R.id.imgHeaderStatus);

        btnInitDevice = findViewById(R.id.btnInitDevice);
        btnUninitDevice = findViewById(R.id.btnUninitDevice);
        btnShowUsers = findViewById(R.id.btnShowUsers);
        btnDeleteOptions = findViewById(R.id.btnDeleteOptions);
    }

    private void setupListeners() {

        btnInitDevice.setOnClickListener(v -> {
            if (bioManager.isReady()) {
                Toast.makeText(this, "Already Initialized", Toast.LENGTH_SHORT).show();
                return;
            }

            if (pendingModel != null) {
                txtBottomMessage.setText("Initializing...");
                new Thread(() -> {
                    DeviceInfo info = new DeviceInfo();
                    bioManager.initDevice(pendingModel, info);

                    runOnUiThread(() -> {
                        if (bioManager.isReady()) {
                            fillDeviceDetails(info);
                            txtBottomMessage.setText("Initialization Success.");
                            btnInitDevice.setEnabled(false);
                            btnUninitDevice.setEnabled(true);
                        } else {
                            txtBottomMessage.setText("Initialization Failed.");
                        }
                    });
                }).start();
            } else {
                Toast.makeText(this, "Connect device first", Toast.LENGTH_SHORT).show();
            }
        });


        btnUninitDevice.setOnClickListener(v -> {
            new Thread(() -> {
                bioManager.uninitDevice();
                runOnUiThread(() -> {
                    // Update UI to "Connected but Uninitialized" state
                    txtDeviceDetails.setText("Make: -\nModel: -\nSerial: -\nW/H: -");
                    txtBottomMessage.setText("Device Uninitialized. Press Init.");

                    btnInitDevice.setEnabled(true);
                    btnUninitDevice.setEnabled(false);

                    Toast.makeText(this, "Device Uninitialized", Toast.LENGTH_SHORT).show();
                });
            }).start();
        });


        cardCapture.setOnClickListener(v -> {
            if (!bioManager.isReady()) {
                Toast.makeText(this, "Please INIT device first!", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(MainActivity.this, EnrollmentActivity.class));
        });

        cardMatch.setOnClickListener(v -> {
            if (!bioManager.isReady()) {
                Toast.makeText(this, "Please INIT device first!", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Match Screen (Coming Soon)", Toast.LENGTH_SHORT).show();
        });

        btnDeleteOptions.setOnClickListener(v -> showDeleteOptionsDialog());

        btnShowUsers.setOnClickListener(v -> {
            // Placeholder for showing user list
            Toast.makeText(this, "Show User List", Toast.LENGTH_SHORT).show();
        });
    }


    @Override
    public void OnDeviceDetection(String deviceName, DeviceDetection detection) {
        runOnUiThread(() -> {
            if (detection == DeviceDetection.CONNECTED) {
                try {
                    pendingModel = DeviceModel.valueOf(deviceName);
                    setUIConnected(true);
                    txtConnectionStatus.setText("Connected (" + deviceName + ")");

                    if (bioManager.isReady()) {
                        // Already init (unlikely on fresh connect, but possible with rapid replug)
                        txtBottomMessage.setText("Device Ready.");
                        btnInitDevice.setEnabled(false);
                        btnUninitDevice.setEnabled(true);
                    } else {
                        // Connected, waiting for Init
                        txtBottomMessage.setText("Device found! Press INIT.");
                        btnInitDevice.setEnabled(true);
                        btnUninitDevice.setEnabled(false);
                    }
                } catch (Exception e) {
                    txtConnectionStatus.setText("Unknown Device");
                }
            } else {
                // Disconnected
                pendingModel = null;
                setUIConnected(false); // Red
                txtBottomMessage.setText("Device Disconnected.");

                // Clear Details immediately
                txtDeviceDetails.setText("Make: -\nModel: -\nSerial: -\nW/H: -");
                btnInitDevice.setEnabled(false);
                btnUninitDevice.setEnabled(false);
            }
        });
    }

    @Override public void OnPreview(int i, int i1, byte[] bytes) {}
    @Override public void OnComplete(int i, int i1, int i2) {}
    @Override public void OnFingerPosition(int i, int i1) {}



    private void setUIConnected(boolean connected) {
        int color = ContextCompat.getColor(this, connected ? R.color.status_connected : R.color.status_disconnected);

        imgConnStatus.setBackgroundColor(color);
        txtConnectionStatus.setTextColor(color);
        if(!connected) txtConnectionStatus.setText("Disconnected");

        imgHeaderStatus.setImageResource(connected ? R.drawable.finger_green : R.drawable.finger_red);
        imgHeaderStatus.setColorFilter(color);
    }

    private void fillDeviceDetails(DeviceInfo info) {
        if (info == null) return;
        String details = String.format("Make: %s\nModel: %s\nSerial: %s\nW/H: %d x %d",
                info.Make, info.Model, info.SerialNo, info.Width, info.Height);
        txtDeviceDetails.setText(details);
    }

    private void refreshUserCount() {
        txtTotalUsers.setText("Registered Users: " + dbHelper.getUserCount());
    }

    private void showDeleteOptionsDialog() {
        String[] options = {"Clear Database Only", "Clear Files Only", "Clear EVERYTHING"};
        selectedDeleteOption = 0;

        new AlertDialog.Builder(this)
                .setTitle("Manage Data")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setSingleChoiceItems(options, 0, (dialog, which) -> {
                    selectedDeleteOption = which;
                })
                .setPositiveButton("DELETE", (dialog, which) -> {
                    String path = getExternalFilesDir(null).getAbsolutePath() + "/FingerData";
                    switch (selectedDeleteOption) {
                        case 0:
                            dbHelper.clearDatabase();
                            Toast.makeText(this, "Database Cleared", Toast.LENGTH_SHORT).show();
                            break;
                        case 1:
                            dbHelper.clearSavedFiles(path);
                            Toast.makeText(this, "Images Deleted", Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            dbHelper.clearAllData(path);
                            Toast.makeText(this, "Full Wipe Complete", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    refreshUserCount();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}