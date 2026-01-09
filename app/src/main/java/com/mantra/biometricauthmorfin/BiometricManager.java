package com.mantra.biometricauthmorfin;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mantra.morfinauth.DeviceInfo;
import com.mantra.morfinauth.MorfinAuth;
import com.mantra.morfinauth.MorfinAuth_Callback;
import com.mantra.morfinauth.enums.DeviceDetection;
import com.mantra.morfinauth.enums.DeviceModel;
import com.mantra.morfinauth.enums.LogLevel;

public class BiometricManager implements MorfinAuth_Callback {

    private static BiometricManager instance;
    private MorfinAuth morfinAuth;
    private MorfinAuth_Callback activeListener;

    // State Tracking
    private boolean isDeviceConnected = false;
    private boolean isDeviceInitialized = false;
    private DeviceModel currentModel;
    private DeviceInfo lastDeviceInfo;

    public static synchronized BiometricManager getInstance(Context context) {
        if (instance == null) {
            instance = new BiometricManager(context.getApplicationContext());
        }
        return instance;
    }

    private BiometricManager(Context context) {
        try {
            morfinAuth = new MorfinAuth(context, this);
            String path = context.getExternalFilesDir(null).getAbsolutePath() + "/FingerData";
            morfinAuth.SetLogProperties(path, LogLevel.ERROR);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setListener(MorfinAuth_Callback listener) {
        this.activeListener = listener;
        // If device is already connected, notify the new listener immediately
        // This ensures the UI updates even if the connection event happened earlier
        if (isDeviceConnected && activeListener != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    activeListener.OnDeviceDetection(currentModel != null ? currentModel.name() : "Device", DeviceDetection.CONNECTED)
            );
        }
    }

    public void removeListener() {
        this.activeListener = null;
    }

    // --- Core Functions ---

    public void initDevice(DeviceModel model, DeviceInfo info) {
        if (morfinAuth == null) return;

        // Prevent double init only if we are truly initialized
        if (isDeviceInitialized && currentModel == model) {
            Log.d("SDK", "BiometricManager: Already Initialized");
            return;
        }

        int ret = morfinAuth.Init(model, info);
        if (ret == 0) {
            isDeviceInitialized = true;
            currentModel = model;
            lastDeviceInfo = info; // Save info to persist across screens
        } else {
            isDeviceInitialized = false;
            lastDeviceInfo = null;
        }
    }

    // Explicitly Uninitialize
    public void uninitDevice() {
        if (morfinAuth != null) {
            morfinAuth.Uninit();
        }
        // Reset Logic Flags
        isDeviceInitialized = false;
        lastDeviceInfo = null;

        // Note: We DO NOT set isDeviceConnected = false here.
        // The USB cable is still physically plugged in.
        // We only want to force re-initialization.
    }

    public MorfinAuth getSDK() { return morfinAuth; }

    // Status Checkers for MainActivity
    public boolean isConnected() { return isDeviceConnected; }
    public boolean isReady() { return isDeviceConnected && isDeviceInitialized; }
    public DeviceInfo getLastDeviceInfo() { return lastDeviceInfo; }
    public DeviceModel getConnectedModel() { return currentModel; }

    // --- SDK Callbacks (Forwarding) ---

    @Override
    public void OnDeviceDetection(String deviceName, DeviceDetection detection) {
        if (detection == DeviceDetection.CONNECTED) {
            isDeviceConnected = true;
            try { currentModel = DeviceModel.valueOf(deviceName); } catch (Exception e) {}
        } else {
            // Physical Disconnect triggers full reset
            isDeviceConnected = false;
            isDeviceInitialized = false;
            currentModel = null;
            lastDeviceInfo = null;
        }

        if (activeListener != null) {
            activeListener.OnDeviceDetection(deviceName, detection);
        }
    }

    @Override
    public void OnPreview(int errorCode, int quality, byte[] image) {
        if (activeListener != null) activeListener.OnPreview(errorCode, quality, image);
    }

    @Override
    public void OnComplete(int errorCode, int quality, int nfiq) {
        if (activeListener != null) activeListener.OnComplete(errorCode, quality, nfiq);
    }

    @Override
    public void OnFingerPosition(int errorCode, int position) {
        if (activeListener != null) activeListener.OnFingerPosition(errorCode, position);
    }
}