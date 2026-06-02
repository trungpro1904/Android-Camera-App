package com.example.myapplication;

import android.content.Context;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Toast;

import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import android.content.ContentValues;
import android.provider.MediaStore;
import androidx.camera.core.ImageCaptureException;
import com.google.common.util.concurrent.ListenableFuture;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Quality;
import androidx.camera.video.Recording;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.core.CameraControl;

import android.graphics.Rect;
import android.media.Image;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

public class CameraManager {
    private static final String TAG = "CameraManager";

    private final Context context;
    private final PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    private int currentLensFacing = CameraSelector.LENS_FACING_BACK;
    private String forcedCameraId = null;
    private float currentZoomRatio = 1.0f;
    private CaptureMode currentMode = CaptureMode.PHOTO;
    private CameraState currentState = CameraState.IDLE;
    private String currentAspectRatioLabel = "4:3";
    private boolean flashEnabled = false;
    private StateCallback stateCallback;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private ImageCapture imageCapture;
    private Preview preview;
    private ImageAnalysis imageAnalysis;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private String currentSelectedLutFile = null;
    private Quality currentQuality = Quality.HD;
    private int currentFps = 30;

    private ObjectDetector objectDetector;
    private FaceDetector faceDetector;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private TrackingListener trackingListener;

    private Rect manualTrackRect = null;
    private long lastTouchTime = 0;
    private static final long TOUCH_TIMEOUT = 5000; // 5 seconds of tracking after touch if not updated

    public interface TrackingListener {
        void onObjectDetected(Rect boundingBox, boolean isEye);
        void onClearTracking();
    }

    public void setTrackingListener(TrackingListener listener) {
        this.trackingListener = listener;
    }

    public void triggerManualTracking(float x, float y) {
        int size = 100;
        manualTrackRect = new Rect((int)(x - size), (int)(y - size), (int)(x + size), (int)(y + size));
        lastTouchTime = System.currentTimeMillis();
        focusOnArea(x, y, 200);
    }

    public interface StateCallback {
        void onStateChanged(CameraState state);
    }

    public CameraManager(Context context, View previewPlaceholder) {
        this.context = context;
        this.previewView = (PreviewView) previewPlaceholder;
        this.cameraProviderFuture = ProcessCameraProvider.getInstance(context);
    }

    public void setStateCallback(StateCallback callback) {
        this.stateCallback = callback;
    }

    private void notifyState(CameraState state) {
        currentState = state;
        if (stateCallback != null) {
            stateCallback.onStateChanged(state);
        }
    }

    public void startCamera(CaptureMode mode) {
        currentMode = mode;
        Log.d(TAG, "startCamera requested with mode: " + mode);
        
        if (cameraProvider != null) {
            bindUseCases();
            return;
        }

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                ContextCompat.getMainExecutor(context).execute(this::bindUseCases);
                notifyState(CameraState.IDLE);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void updateAspectRatio(String ratioLabel) {
        this.currentAspectRatioLabel = ratioLabel;
        if (cameraProvider != null) {
            bindUseCases();
        }
    }

    public void updateVideoConfig(String qualityStr, int fps) {
        if ("4K".equals(qualityStr)) this.currentQuality = Quality.UHD;
        else if ("720".equals(qualityStr)) this.currentQuality = Quality.SD;
        else this.currentQuality = Quality.HD;
        
        this.currentFps = fps;
        if (cameraProvider != null && currentMode == CaptureMode.VIDEO) {
            bindUseCases();
        }
    }

    @androidx.annotation.OptIn(markerClass = {androidx.camera.camera2.interop.ExperimentalCamera2Interop.class})
    private void bindUseCases() {
        if (cameraProvider == null) return;

        Log.d(TAG, "Binding use cases for mode: " + currentMode + ", Camera ID: " + forcedCameraId + ", Ratio: " + currentAspectRatioLabel);
        cameraProvider.unbindAll();
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }

        CameraSelector cameraSelector;
        if (forcedCameraId != null && currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            cameraSelector = new CameraSelector.Builder()
                    .addCameraFilter(cameraInfos -> {
                        List<androidx.camera.core.CameraInfo> filtered = new ArrayList<>();
                        for (androidx.camera.core.CameraInfo info : cameraInfos) {
                            String id = androidx.camera.camera2.interop.Camera2CameraInfo.from(info).getCameraId();
                            if (id.equals(forcedCameraId)) {
                                filtered.add(info);
                            }
                        }
                        return filtered;
                    })
                    .build();
        } else {
            cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(currentLensFacing)
                    .build();
        }

        int aspectRatio;
        if (currentAspectRatioLabel.equals("16:9")) {
            aspectRatio = AspectRatio.RATIO_16_9;
        } else {
            // For 4:3 and 1:1, we use 4:3 AspectRatio. 1:1 will be cropped in UI.
            aspectRatio = AspectRatio.RATIO_4_3;
        }

        preview = new Preview.Builder()
                .setTargetAspectRatio(aspectRatio)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Setup Object Detection
        ObjectDetectorOptions objectOptions = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .build();
        objectDetector = ObjectDetection.getClient(objectOptions);

        // Setup Face Detection
        FaceDetectorOptions faceOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();
        faceDetector = FaceDetection.getClient(faceOptions);

        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            @ExperimentalGetImage
            Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                imageProxy.close();
                return;
            }

            InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            // 1. Check if we have a manual touch target
            if (manualTrackRect != null && System.currentTimeMillis() - lastTouchTime < TOUCH_TIMEOUT) {
                objectDetector.process(inputImage)
                        .addOnSuccessListener(objects -> {
                            DetectedObject bestMatch = null;
                            for (DetectedObject obj : objects) {
                                if (Rect.intersects(obj.getBoundingBox(), manualTrackRect)) {
                                    bestMatch = obj;
                                    break;
                                }
                            }
                            if (bestMatch != null) {
                                manualTrackRect = bestMatch.getBoundingBox();
                                lastTouchTime = System.currentTimeMillis();
                                if (trackingListener != null) {
                                    trackingListener.onObjectDetected(manualTrackRect, false);
                                }
                            }
                        })
                        .addOnCompleteListener(task -> imageProxy.close());
                return;
            }

            // 2. If no manual target, check for Faces/Eyes
            faceDetector.process(inputImage)
                    .addOnSuccessListener(faces -> {
                        if (faces.isEmpty()) {
                            if (trackingListener != null) trackingListener.onClearTracking();
                            return;
                        }

                        // Pick the "closest" face (largest area)
                        Face closestFace = null;
                        int maxArea = 0;
                        for (Face face : faces) {
                            Rect bounds = face.getBoundingBox();
                            int area = bounds.width() * bounds.height();
                            if (area > maxArea) {
                                maxArea = area;
                                closestFace = face;
                            }
                        }

                        if (closestFace != null) {
                            // Check for eyes (Priority 2)
                            FaceLandmark leftEye = closestFace.getLandmark(FaceLandmark.LEFT_EYE);
                            FaceLandmark rightEye = closestFace.getLandmark(FaceLandmark.RIGHT_EYE);
                            
                            // Pick one eye (whichever is available, or can pick based on some logic)
                            FaceLandmark targetEye = (leftEye != null) ? leftEye : rightEye;

                            if (targetEye != null) {
                                // Eye box 20x20
                                int eyeX = (int) targetEye.getPosition().x;
                                int eyeY = (int) targetEye.getPosition().y;
                                Rect eyeRect = new Rect(eyeX - 10, eyeY - 10, eyeX + 10, eyeY + 10);
                                if (trackingListener != null) trackingListener.onObjectDetected(eyeRect, true);
                            } else {
                                // Face box (Priority 3)
                                if (trackingListener != null) trackingListener.onObjectDetected(closestFace.getBoundingBox(), false);
                            }
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        });

        try {
            if (currentMode == CaptureMode.PHOTO) {
                videoCapture = null;
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetAspectRatio(aspectRatio)
                        .setFlashMode(flashEnabled ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF)
                        .build();
                camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, preview, imageCapture, imageAnalysis);
            } else {
                imageCapture = null;
                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(currentQuality))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);
                camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, preview, videoCapture, imageAnalysis);
            }
            
            if (forcedCameraId == null && camera != null && currentLensFacing == CameraSelector.LENS_FACING_BACK) {
                forcedCameraId = androidx.camera.camera2.interop.Camera2CameraInfo.from(camera.getCameraInfo()).getCameraId();
            }
            
            applyZoomInternal();
            Log.d(TAG, "Binding successful");
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
            if (forcedCameraId != null) {
                forcedCameraId = null;
                bindUseCases();
            }
        }
    }

    public void setZoomRatio(float ratio) {
        currentZoomRatio = ratio;
        if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) {
            applyZoomInternal();
            return;
        }

        HardwareInfo info = HardwareInfo.getInstance();
        String targetId = null;

        // Find optimal physical lens: highest zoomRatio that is <= target ratio
        for (HardwareInfo.CameraModule mod : info.cameraModules) {
            if (mod.isBack && mod.zoomRatio <= ratio) {
                targetId = mod.id;
            }
        }

        if (targetId != null && !targetId.equals(forcedCameraId)) {
            Log.d(TAG, "Switching physical lens to: " + targetId + " for ratio: " + ratio);
            forcedCameraId = targetId;
            bindUseCases();
        } else {
            applyZoomInternal();
        }
    }

    private void applyZoomInternal() {
        if (camera == null) return;
        
        float lensBaseRatio = 1.0f;
        if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            for (HardwareInfo.CameraModule mod : HardwareInfo.getInstance().cameraModules) {
                if (mod.id.equals(forcedCameraId)) {
                    lensBaseRatio = mod.zoomRatio;
                    break;
                }
            }
        }
        
        float digitalZoom = currentZoomRatio / lensBaseRatio;
        try {
            camera.getCameraControl().setZoomRatio(Math.max(1.0f, digitalZoom));
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply zoom", e);
        }
    }

    public void switchCamera() {
        currentLensFacing = (currentLensFacing == CameraSelector.LENS_FACING_BACK) ?
                CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        forcedCameraId = null;
        bindUseCases();
        Toast.makeText(context, "Switched camera", Toast.LENGTH_SHORT).show();
    }

    public void setExposureCompensation(int index) {
        if (camera != null) {
            camera.getCameraControl().setExposureCompensationIndex(index);
        }
    }

    public void setFlashEnabled(boolean enabled) {
        this.flashEnabled = enabled;
        if (imageCapture != null) {
            imageCapture.setFlashMode(enabled ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
        }
        if (currentMode == CaptureMode.VIDEO && camera != null) {
            camera.getCameraControl().enableTorch(enabled);
        }
    }

    public void takePhoto() {
        if (currentMode != CaptureMode.PHOTO || imageCapture == null) {
            Toast.makeText(context, "Switch to Photo mode", Toast.LENGTH_SHORT).show();
            return;
        }

        notifyState(CameraState.CAPTURING);

        imageCapture.takePicture(ContextCompat.getMainExecutor(context), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap originalBitmap = imageProxyToBitmap(image);
                image.close();

                if (originalBitmap == null) {
                    notifyState(CameraState.IDLE);
                    return;
                }

                Bitmap finalBitmap;
                if (currentSelectedLutFile != null) {
                    Bitmap lutBitmap = HaldLutProcessor.loadLutFromAssets(context, currentSelectedLutFile);
                    finalBitmap = HaldLutProcessor.applyHaldLut8(originalBitmap, lutBitmap);
                } else {
                    finalBitmap = originalBitmap;
                }

                saveBitmapToStorage(finalBitmap);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Capture failed", exception);
                notifyState(CameraState.IDLE);
            }
        });
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        // Correct rotation
        Matrix matrix = new Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees());

        // Handle front camera mirroring
        if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) {
            matrix.postScale(-1f, 1f, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void saveBitmapToStorage(Bitmap bitmap) {
        String name = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera");

        try {
            android.net.Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            if (uri != null) {
                try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                    Toast.makeText(context, "Photo saved", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save photo", e);
        } finally {
            notifyState(CameraState.IDLE);
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    public void startRecording() {
        if (currentMode != CaptureMode.VIDEO || videoCapture == null) return;

        String name = "VID_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera");

        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(
                context.getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();

        activeRecording = videoCapture.getOutput()
                .prepareRecording(context, options)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context), event -> {
                    if (event instanceof VideoRecordEvent.Start) {
                        notifyState(CameraState.RECORDING);
                    } else if (event instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) event;
                        if (!finalizeEvent.hasError()) {
                            Toast.makeText(context, "Video saved", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "Video recording error: " + finalizeEvent.getError());
                        }
                        notifyState(CameraState.IDLE);
                    }
                });
    }

    public void stopRecording() {
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
    }

    public void focusOnArea(float x, float y, int size) {
        if (camera == null) return;
        
        androidx.camera.core.MeteringPointFactory factory = previewView.getMeteringPointFactory();
        androidx.camera.core.MeteringPoint point = factory.createPoint(x, y, size / (float)previewView.getWidth());
        androidx.camera.core.FocusMeteringAction action = new androidx.camera.core.FocusMeteringAction.Builder(point, androidx.camera.core.FocusMeteringAction.FLAG_AF | androidx.camera.core.FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        
        camera.getCameraControl().startFocusAndMetering(action);
    }

    public void setCurrentLut(String lutFile) {
        this.currentSelectedLutFile = lutFile;
    }
}
