package com.example.myapplication;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;

import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

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

    // EV info
    public int evMin = -6;
    public int evMax = 6;
    public float evStep = 0.333333f;

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
        // Since CameraX initialization is async, we use a future
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        try {
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
            scanWithCameraX(context, cameraProvider);
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Failed to get CameraProvider", e);
            // Fallback to basic scan if CameraX fails
            scanHardwareLegacy(context);
        }
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)
    private void scanWithCameraX(Context context, ProcessCameraProvider cameraProvider) {
        List<CameraInfo> availableCameras = cameraProvider.getAvailableCameraInfos();
        if (availableCameras.isEmpty()) return;

        List<CameraModule> tempModules = new ArrayList<>();
        float mainEqFocLength = 0;
        videoCapabilities.clear();
        supportedMpList.clear();
        supportedMpList.add(12);

        for (CameraInfo info : availableCameras) {
            Camera2CameraInfo c2Info = Camera2CameraInfo.from(info);
            String id = c2Info.getCameraId();
            
            Integer facing = c2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING);
            boolean isBack = (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK);

            // Focal Length calculation
            SizeF sensorSize = c2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            float[] focalLengths = c2Info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);

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
            StreamConfigurationMap map = c2Info.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
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
            }

            // Video Support using CameraX QualitySelector
            if (isBack && videoCapabilities.isEmpty()) {
                List<Quality> supportedQualities = QualitySelector.getSupportedQualities(info);
                
                if (supportedQualities.contains(Quality.UHD)) {
                    videoCapabilities.add(createVideoCap(info, Quality.UHD, "4K", map));
                }
                if (supportedQualities.contains(Quality.FHD)) {
                    videoCapabilities.add(createVideoCap(info, Quality.FHD, "HD", map));
                }
                if (supportedQualities.contains(Quality.HD)) {
                    videoCapabilities.add(createVideoCap(info, Quality.HD, "720", map));
                }
            }

            // EV info
            if (isBack) {
                Range<Integer> evRange = c2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
                android.util.Rational step = c2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
                if (evRange != null && step != null) {
                    this.evMin = evRange.getLower();
                    this.evMax = evRange.getUpper();
                    this.evStep = step.floatValue();
                }
            }
        }

        finalizeScan(tempModules, mainEqFocLength);
    }

    private VideoCapability createVideoCap(CameraInfo info, Quality quality, String label, StreamConfigurationMap map) {
        VideoCapability cap = new VideoCapability();
        cap.qualityLabel = label;

        if (map == null) {
            cap.supportedFps.add(30);
            return cap;
        }

        // Mapping CameraX Quality to roughly standard resolutions for FPS checking
        Size targetSize;
        if (quality == Quality.UHD) targetSize = new Size(3840, 2160);
        else if (quality == Quality.FHD) targetSize = new Size(1920, 1080);
        else targetSize = new Size(1280, 720);

        // Find closest supported size in Camera2 map for MediaRecorder
        Size[] sizes = map.getOutputSizes(android.media.MediaRecorder.class);
        Size bestMatch = null;
        if (sizes != null) {
            for (Size s : sizes) {
                if (s.getWidth() == targetSize.getWidth() && s.getHeight() == targetSize.getHeight()) {
                    bestMatch = s;
                    break;
                }
            }
        }

        if (bestMatch != null) {
            long minDuration = map.getOutputMinFrameDuration(android.media.MediaRecorder.class, bestMatch);
            int maxFps = (minDuration > 0) ? (int) (1_000_000_000L / minDuration) : 30;

            if (maxFps >= 30) cap.supportedFps.add(30);
            if (maxFps >= 60) cap.supportedFps.add(60);
            if (maxFps >= 120) cap.supportedFps.add(120);

            // High speed check
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
        } else {
            cap.supportedFps.add(30);
        }

        Collections.sort(cap.supportedFps);
        return cap;
    }

    private void finalizeScan(List<CameraModule> tempModules, float mainEqFocLength) {
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
    }

    // Legacy fallback using Camera2 directly
    private void scanHardwareLegacy(Context context) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = manager.getCameraIdList();
            List<CameraModule> tempModules = new ArrayList<>();
            float mainEqFocLength = 0;

            for (String id : cameraIds) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                boolean isBack = (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK);

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
                    }
                }
            }
            finalizeScan(tempModules, mainEqFocLength);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Legacy scan failed", e);
        }
    }
}
