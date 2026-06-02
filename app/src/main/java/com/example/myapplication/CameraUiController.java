package com.example.myapplication;

public interface CameraUiController {
    /**
     * Called when the user clicks the shutter button.
     */
    void onShutterClicked();

    /**
     * Called when the user wants to switch the camera (front/back).
     */
    void onSwitchCameraClicked();

    /**
     * Called when the user selects a zoom level.
     * @param zoomLevel The selected zoom level (e.g., 0.5, 1.0, 2.0).
     */
    void onZoomSelected(float zoomLevel);

    /**
     * Called when the photo/video mode changes.
     * @param isVideoMode true if Video mode is selected, false for Photo mode.
     */
    void onModeChanged(boolean isVideoMode);

    /**
     * Called when a preset is selected from the sliding panel.
     * @param presetIndex The index of the selected preset.
     */
    void onPresetSelected(int presetIndex);
}
