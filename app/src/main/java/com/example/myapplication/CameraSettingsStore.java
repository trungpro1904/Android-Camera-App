package com.example.myapplication;

public class CameraSettingsStore {
    private String currentResolution = "12mp";
    private String currentAspectRatio = "4:3";
    private float currentEv = 0f;
    private int currentPreset = 0;
    private CaptureMode captureMode = CaptureMode.PHOTO;
    private CameraState cameraState = CameraState.IDLE;
    private boolean flashEnabled = false;
    private int currentFps = 30;
    private String currentVideoQuality = "HD";

    // New settings
    private String photoFormat = "JPEG"; // JPEG, RAW, RAW+JPEG
    private String rawCompression = "None"; // None, Lossless, Lossy
    private String startupMode = "Previous"; // Previous, Basic
    private String volumeButtonFunc = "Zoom"; // Zoom, Shutter
    private String touchFunc = "Tracking"; // Tracking, FocusExposure
    private String currentLutFile = null;

    public String getCurrentResolution() {
        return currentResolution;
    }

    public void setCurrentResolution(String currentResolution) {
        this.currentResolution = currentResolution;
    }

    public String getCurrentAspectRatio() {
        return currentAspectRatio;
    }

    public void setCurrentAspectRatio(String currentAspectRatio) {
        this.currentAspectRatio = currentAspectRatio;
    }

    public float getCurrentEv() {
        return currentEv;
    }

    public void setCurrentEv(float currentEv) {
        this.currentEv = currentEv;
    }

    public int getCurrentPreset() {
        return currentPreset;
    }

    public void setCurrentPreset(int currentPreset) {
        this.currentPreset = currentPreset;
    }

    public CaptureMode getCaptureMode() {
        return captureMode;
    }

    public void setCaptureMode(CaptureMode captureMode) {
        this.captureMode = captureMode;
    }

    public CameraState getCameraState() {
        return cameraState;
    }

    public void setCameraState(CameraState cameraState) {
        this.cameraState = cameraState;
    }

    public boolean isFlashEnabled() {
        return flashEnabled;
    }

    public void setFlashEnabled(boolean flashEnabled) {
        this.flashEnabled = flashEnabled;
    }

    public int getCurrentFps() {
        return currentFps;
    }

    public void setCurrentFps(int currentFps) {
        this.currentFps = currentFps;
    }

    public String getCurrentVideoQuality() {
        return currentVideoQuality;
    }

    public void setCurrentVideoQuality(String currentVideoQuality) {
        this.currentVideoQuality = currentVideoQuality;
    }

    public String getPhotoFormat() { return photoFormat; }
    public void setPhotoFormat(String photoFormat) { this.photoFormat = photoFormat; }

    public String getRawCompression() { return rawCompression; }
    public void setRawCompression(String rawCompression) { this.rawCompression = rawCompression; }

    public String getStartupMode() { return startupMode; }
    public void setStartupMode(String startupMode) { this.startupMode = startupMode; }

    public String getVolumeButtonFunc() { return volumeButtonFunc; }
    public void setVolumeButtonFunc(String volumeButtonFunc) { this.volumeButtonFunc = volumeButtonFunc; }

    public String getTouchFunc() { return touchFunc; }
    public void setTouchFunc(String touchFunc) { this.touchFunc = touchFunc; }

    public String getCurrentLutFile() { return currentLutFile; }
    public void setCurrentLutFile(String currentLutFile) { this.currentLutFile = currentLutFile; }
}
