package com.example.myapplication;

import android.util.Log;

public class CommandInvoker implements IBasicCameraEngine, CameraManager.StateCallback {
    private static final String TAG = "CommandInvoker";
    private final CameraManager cameraManager;
    private final CameraSettingsStore settingsStore;

    public CommandInvoker(CameraManager cameraManager, CameraSettingsStore settingsStore) {
        this.cameraManager = cameraManager;
        this.settingsStore = settingsStore;
        this.cameraManager.setStateCallback(this);
    }

    @Override
    public void setZoomRatio(float ratio) {
        Log.d(TAG, "setZoomRatio: " + ratio);
        cameraManager.setZoomRatio(ratio);
    }

    @Override
    public void setCaptureMode(CaptureMode mode) {
        Log.d(TAG, "setCaptureMode: " + mode);
        settingsStore.setCaptureMode(mode);
        cameraManager.startCamera(mode);
    }

    @Override
    public void updateConfiguration(ConfigType type, Object value) {
        Log.d(TAG, "updateConfiguration: " + type + " = " + value);
        switch (type) {
            case RESOLUTION:
                settingsStore.setCurrentResolution((String) value);
                break;
            case ASPECT_RATIO:
                String ratio = (String) value;
                settingsStore.setCurrentAspectRatio(ratio);
                cameraManager.updateAspectRatio(ratio);
                break;
            case EV:
                float evValue = (float) value;
                settingsStore.setCurrentEv(evValue);
                // Convert EV value to index based on step
                float step = HardwareInfo.getInstance().evStep;
                int index = Math.round(evValue / (step != 0 ? step : 0.3333f));
                cameraManager.setExposureCompensation(index);
                break;
            case FLASH:
                boolean flashOn = (boolean) value;
                settingsStore.setFlashEnabled(flashOn);
                cameraManager.setFlashEnabled(flashOn);
                break;
            case FPS:
                int fps = (int) value;
                settingsStore.setCurrentFps(fps);
                cameraManager.updateVideoConfig(settingsStore.getCurrentVideoQuality(), fps);
                break;
            case VIDEO_QUALITY:
                String quality = (String) value;
                settingsStore.setCurrentVideoQuality(quality);
                cameraManager.updateVideoConfig(quality, settingsStore.getCurrentFps());
                break;
        }
    }

    @Override
    public void applyPreset(int presetId) {
        Log.d(TAG, "applyPreset: " + presetId);
        settingsStore.setCurrentPreset(presetId);
    }

    @Override
    public void takePhoto() {
        if (settingsStore.getCameraState() == CameraState.IDLE && settingsStore.getCaptureMode() == CaptureMode.PHOTO) {
            cameraManager.takePhoto();
        }
    }

    @Override
    public void startRecording() {
        if (settingsStore.getCameraState() == CameraState.IDLE && settingsStore.getCaptureMode() == CaptureMode.VIDEO) {
            cameraManager.startRecording();
        }
    }

    @Override
    public void stopRecording() {
        if (settingsStore.getCameraState() == CameraState.RECORDING) {
            cameraManager.stopRecording();
        }
    }

    @Override
    public void toggleFlash() {
        boolean newState = !settingsStore.isFlashEnabled();
        updateConfiguration(ConfigType.FLASH, newState);
    }

    public void switchCamera() {
        cameraManager.switchCamera();
    }

    @Override
    public void onStateChanged(CameraState state) {
        settingsStore.setCameraState(state);
        Log.d(TAG, "Camera State Changed: " + state);
    }
}
