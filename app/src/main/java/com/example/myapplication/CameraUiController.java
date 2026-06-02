package com.example.myapplication;

public interface CameraUiController {
    void onShutterClicked();

    /**
     * xoay cam trước sau
     */
    void onSwitchCameraClicked();

    /**
     * gọi khi user chọn mức zoom
     * @param zoomLevel 0.5x, 1x,...
     */
    void onZoomSelected(float zoomLevel);

    /**
     * đổi mode quay/chụp
     * @param isVideoMode true nếu quay, false nếu chụp
     */
    void onModeChanged(boolean isVideoMode);

    /**
     * gọi khi đổi LUTs
     * @param presetIndex
     */
    void onPresetSelected(int presetIndex);
}
