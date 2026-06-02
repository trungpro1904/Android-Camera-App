package com.example.myapplication;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Range;
import android.util.SizeF;
import android.util.Size;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HardwareInfo {
    private static final String TAG = "HardwareInfo";
    private static HardwareInfo instance;

    public static class CameraModule {
        public String id;
        public float eqFocalLength;
        public float zoomRatio;
        public boolean isBack;
    }

    public static class VideoCapability {
        public String qualityLabel; // "4K", "HD", "720"
        public List<Integer> supportedFps = new ArrayList<>();
    }

    public List<CameraModule> cameraModules = new CopyOnWriteArrayList<>();
    public int maxPhotoResolutionMp = 12; 
    public List<Integer> supportedMpList = new CopyOnWriteArrayList<>();
    public List<VideoCapability> videoCapabilities = new CopyOnWriteArrayList<>();
    public List<Float> zoomRatios = new CopyOnWriteArrayList<>();
    public float baseFocalLength = 26f;

    // EV info - defaults to standard -2 to +2 range with 1/3 steps
    public int evMin = -6;
    public int evMax = 6;
    public float evStep = 0.333333f;

    // Video info
    public boolean supports4k = false;
    public boolean supports1080p = true;
    public boolean supports720p = true;
    public List<Integer> supportedFps = new CopyOnWriteArrayList<>();

    public int cameraModulesCount = 1;
    private boolean isInitialized = false;

    private HardwareInfo() {
        supportedMpList.add(12);
        zoomRatios.add(1.0f);
    }

    public static synchronized HardwareInfo getInstance() {
        if (instance == null) {
            instance = new HardwareInfo();
        }
        return instance;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void scanHardware(Context context) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = manager.getCameraIdList();
            if (cameraIds.length == 0) return;

            List<CameraModule> tempModules = new ArrayList<>();
            float mainEqFocLength = 0;
            videoCapabilities.clear();
            supportedMpList.clear();
            supportedMpList.add(12); // Always support at least 12

            for (String id : cameraIds) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                boolean isBack = (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK);

                // Only scan capabilities for the main/default back camera for UI purposes
                // Real lens switching will happen in CameraManager
                boolean isPrimaryBack = isBack && tempModules.isEmpty();

                SizeF sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);

                float eqFoc = 0;
                if (sensorSize != null && focalLengths != null && focalLengths.length > 0) {
                    float w = sensorSize.getWidth();
                    float h = sensorSize.getHeight();
                    float diag = (float) Math.sqrt(w * w + h * h);
                    float cropFactor = 43.27f / diag;
                    eqFoc = focalLengths[0] * cropFactor;
                }

                if (isBack && eqFoc > 0) {
                    CameraModule mod = new CameraModule();
                    mod.id = id;
                    mod.eqFocalLength = eqFoc;
                    mod.isBack = true;
                    tempModules.add(mod);

                    if (eqFoc >= 22f && eqFoc <= 29f && mainEqFocLength == 0) {
                        mainEqFocLength = eqFoc;
                        baseFocalLength = eqFoc;
                    }
                }

                // MP Support
                StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map != null && isBack) {
                    Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
                    if (sizes != null && sizes.length > 0) {
                        int maxMp = 0;
                        for (Size s : sizes) {
                            int mp = Math.round((s.getWidth() * s.getHeight()) / 1000000f);
                            if (mp > maxMp) maxMp = mp;
                        }
                        if (maxMp > this.maxPhotoResolutionMp) this.maxPhotoResolutionMp = maxMp;

                        if (maxMp >= 48 && !supportedMpList.contains(48)) supportedMpList.add(48);
                        if (maxMp >= 24 && !supportedMpList.contains(24)) supportedMpList.add(24);
                    }

                    // Video Support (Scan for primary back camera)
                    if (isPrimaryBack) {
                        checkVideoSupport(map);
                    }
                }

                // Update EV range and step if available for the main camera
                if (isBack) {
                    Range<Integer> evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
                    android.util.Rational step = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
                    if (evRange != null && step != null) {
                        this.evMin = evRange.getLower();
                        this.evMax = evRange.getUpper();
                        this.evStep = step.floatValue();
                        Log.d(TAG, "Hardware EV: [" + evMin + ", " + evMax + "], step: " + evStep);
                    }
                }
            }
            
            if (mainEqFocLength == 0 && !tempModules.isEmpty()) {
                mainEqFocLength = tempModules.get(0).eqFocalLength;
            }

            if (mainEqFocLength > 0) {
                List<Float> tempRatios = new ArrayList<>();
                for (CameraModule mod : tempModules) {
                    float ratio = mod.eqFocalLength / mainEqFocLength;
                    ratio = Math.round(ratio * 10f) / 10f;
                    mod.zoomRatio = ratio;
                    if (!tempRatios.contains(ratio)) {
                        tempRatios.add(ratio);
                    }
                }
                Collections.sort(tempRatios);
                zoomRatios.clear();
                zoomRatios.addAll(tempRatios);
            }

            Collections.sort(tempModules, (a, b) -> Float.compare(a.zoomRatio, b.zoomRatio));
            cameraModules.clear();
            cameraModules.addAll(tempModules);
            this.cameraModulesCount = cameraModules.size();
            isInitialized = true;
            Log.d(TAG, "Hardware scan complete. Modules: " + cameraModulesCount + ", Ratios: " + zoomRatios);

        } catch (CameraAccessException | AssertionError e) {
            Log.e(TAG, "Error scanning hardware", e);
        }
    }

    private void checkVideoSupport(StreamConfigurationMap map) {
        // We define targeting sizes
        Size size4K = new Size(3840, 2160);
        Size size1080p = new Size(1920, 1080);
        Size size720p = new Size(1280, 720);

        videoCapabilities.add(getVideoCap(map, size4K, "4K"));
        videoCapabilities.add(getVideoCap(map, size1080p, "HD"));
        videoCapabilities.add(getVideoCap(map, size720p, "720"));
    }

    private VideoCapability getVideoCap(StreamConfigurationMap map, Size targetSize, String label) {
        VideoCapability cap = new VideoCapability();
        cap.qualityLabel = label;

        // Find closest supported size
        Size[] sizes = map.getOutputSizes(android.media.MediaRecorder.class);
        Size bestMatch = null;
        for (Size s : sizes) {
            if (s.getWidth() == targetSize.getWidth() && s.getHeight() == targetSize.getHeight()) {
                bestMatch = s;
                break;
            }
        }

        if (bestMatch != null) {
            // Check standard FPS
            long minDuration = map.getOutputMinFrameDuration(android.media.MediaRecorder.class, bestMatch);
            int maxStandardFps = (minDuration > 0) ? (int) (1_000_000_000L / minDuration) : 30;

            if (maxStandardFps >= 30) cap.supportedFps.add(30);
            if (maxStandardFps >= 60) cap.supportedFps.add(60);
            if (maxStandardFps >= 120) cap.supportedFps.add(120);

            // Also check high speed ranges if available
            try {
                Range<Integer>[] hsRanges = map.getHighSpeedVideoFpsRangesFor(bestMatch);
                if (hsRanges != null) {
                    for (Range<Integer> r : hsRanges) {
                        if (r.getUpper() >= 120 && !cap.supportedFps.contains(120)) {
                            cap.supportedFps.add(120);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        Collections.sort(cap.supportedFps);
        return cap;
    }
}
