package com.example.myapplication;

public interface IBasicCameraEngine {
    // Zoom: [0.5x, 1.0x, 2.0x]
    void setZoomRatio(float ratio);

    // Mode: Chụp ảnh hoặc Quay phim
    void setCaptureMode(CaptureMode mode);

    // Thuộc tính: 48mp/24mp/12mp, EV, Aspect Ratio 4:3/16:9
    void updateConfiguration(ConfigType type, Object value);

    // Preset: Áp dụng bộ lọc màu
    void applyPreset(int presetId);

    // Hành động chính
    void takePhoto();
    void startRecording();
    void stopRecording();
    void toggleFlash();
}
