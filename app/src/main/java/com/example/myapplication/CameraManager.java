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
import androidx.camera.video.FallbackStrategy;
import androidx.camera.core.CameraControl;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import android.hardware.camera2.CameraCharacteristics;

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

import android.location.Location;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import androidx.exifinterface.media.ExifInterface;
import java.io.File;
import java.io.FileOutputStream;
import android.content.SharedPreferences;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;

public class CameraManager {
    private static final String TAG = "CameraManager";

    private final Context context;
    private final PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private FusedLocationProviderClient fusedLocationClient;

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
    private static final long TOUCH_TIMEOUT = 5000;

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
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
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
        else if ("720".equals(qualityStr)) this.currentQuality = Quality.HD;
        else this.currentQuality = Quality.FHD;
        
        this.currentFps = fps;
        if (cameraProvider != null && currentMode == CaptureMode.VIDEO) {
            bindUseCases();
        }
    }

    @androidx.annotation.OptIn(markerClass = {androidx.camera.camera2.interop.ExperimentalCamera2Interop.class})
    private void bindUseCases() {
        if (cameraProvider == null) return;

        Log.d(TAG, "Binding use cases for mode: " + currentMode + ", Camera ID: " + forcedCameraId);
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

        int aspectRatio = currentAspectRatioLabel.equals("16:9") ? AspectRatio.RATIO_16_9 : AspectRatio.RATIO_4_3;

        preview = new Preview.Builder().setTargetAspectRatio(aspectRatio).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        setupDetectors();

        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

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
                QualitySelector qualitySelector = QualitySelector.from(currentQuality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD));
                Recorder recorder = new Recorder.Builder().setQualitySelector(qualitySelector).build();
                videoCapture = VideoCapture.withOutput(recorder);
                camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, preview, videoCapture, imageAnalysis);
            }
            
            if (forcedCameraId == null && camera != null && currentLensFacing == CameraSelector.LENS_FACING_BACK) {
                forcedCameraId = androidx.camera.camera2.interop.Camera2CameraInfo.from(camera.getCameraInfo()).getCameraId();
            }
            applyZoomInternal();
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void setupDetectors() {
        if (objectDetector == null) {
            objectDetector = ObjectDetection.getClient(new ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE).enableClassification().build());
        }
        if (faceDetector == null) {
            faceDetector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).build());
        }
    }

    @androidx.annotation.OptIn(markerClass = {androidx.camera.core.ExperimentalGetImage.class})
    private void analyzeImage(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) { imageProxy.close(); return; }
        InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        if (manualTrackRect != null && System.currentTimeMillis() - lastTouchTime < TOUCH_TIMEOUT) {
            objectDetector.process(inputImage).addOnSuccessListener(objects -> {
                for (DetectedObject obj : objects) {
                    if (Rect.intersects(obj.getBoundingBox(), manualTrackRect)) {
                        manualTrackRect = obj.getBoundingBox();
                        lastTouchTime = System.currentTimeMillis();
                        if (trackingListener != null) trackingListener.onObjectDetected(manualTrackRect, false);
                        break;
                    }
                }
            }).addOnCompleteListener(task -> imageProxy.close());
            return;
        }

        faceDetector.process(inputImage).addOnSuccessListener(faces -> {
            if (faces.isEmpty()) { if (trackingListener != null) trackingListener.onClearTracking(); return; }
            Face target = null;
            int maxA = -1;
            for (Face f : faces) {
                int area = f.getBoundingBox().width() * f.getBoundingBox().height();
                if (area > maxA) {
                    maxA = area;
                    target = f;
                }
            }
            if (target != null) {
                FaceLandmark eye = target.getLandmark(FaceLandmark.LEFT_EYE);
                if (eye == null) eye = target.getLandmark(FaceLandmark.RIGHT_EYE);
                if (eye != null) {
                    Rect eyeRect = new Rect((int)eye.getPosition().x-10, (int)eye.getPosition().y-10, (int)eye.getPosition().x+10, (int)eye.getPosition().y+10);
                    if (trackingListener != null) trackingListener.onObjectDetected(eyeRect, true);
                } else if (trackingListener != null) trackingListener.onObjectDetected(target.getBoundingBox(), false);
            }
        }).addOnCompleteListener(task -> imageProxy.close());
    }

    public void setZoomRatio(float ratio) {
        currentZoomRatio = ratio;
        if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) { applyZoomInternal(); return; }
        HardwareInfo info = HardwareInfo.getInstance();
        String targetId = null;
        for (HardwareInfo.CameraModule mod : info.cameraModules) {
            if (mod.isBack && mod.zoomRatio <= ratio) targetId = mod.id;
        }
        if (targetId != null && !targetId.equals(forcedCameraId)) {
            forcedCameraId = targetId;
            bindUseCases();
        } else applyZoomInternal();
    }

    private void applyZoomInternal() {
        if (camera == null) return;
        float lensBaseRatio = 1.0f;
        if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            for (HardwareInfo.CameraModule mod : HardwareInfo.getInstance().cameraModules) {
                if (mod.id.equals(forcedCameraId)) { lensBaseRatio = mod.zoomRatio; break; }
            }
        }
        try { camera.getCameraControl().setZoomRatio(Math.max(1.0f, currentZoomRatio / lensBaseRatio)); } catch (Exception ignored) {}
    }

    public void switchCamera() {
        currentLensFacing = (currentLensFacing == CameraSelector.LENS_FACING_BACK) ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        forcedCameraId = null;
        bindUseCases();
    }

    public void setExposureCompensation(int index) { if (camera != null) camera.getCameraControl().setExposureCompensationIndex(index); }
    public void setFlashEnabled(boolean enabled) {
        this.flashEnabled = enabled;
        if (imageCapture != null) imageCapture.setFlashMode(enabled ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
        if (currentMode == CaptureMode.VIDEO && camera != null) camera.getCameraControl().enableTorch(enabled);
    }

    public void takePhoto() {
        if (currentMode != CaptureMode.PHOTO || imageCapture == null) return;
        notifyState(CameraState.CAPTURING);
        imageCapture.takePicture(ContextCompat.getMainExecutor(context), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap originalBitmap = imageProxyToBitmap(image);
                image.close();
                if (originalBitmap == null) { notifyState(CameraState.IDLE); return; }
                
                Bitmap finalBitmap = (currentSelectedLutFile != null) ? 
                    HaldLutProcessor.applyHaldLut8(originalBitmap, HaldLutProcessor.loadLutFromAssets(context, currentSelectedLutFile)) : originalBitmap;

                saveBitmapToStorage(finalBitmap);
            }
            @Override public void onError(@NonNull ImageCaptureException exception) { Log.e(TAG, "Capture failed", exception); notifyState(CameraState.IDLE); }
        });
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        Matrix matrix = new Matrix(); matrix.postRotate(image.getImageInfo().getRotationDegrees());
        if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) matrix.postScale(-1f, 1f, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)
    private void saveBitmapToStorage(Bitmap bitmap) {
        SharedPreferences prefs = context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE);
        int imgCount = prefs.getInt("img_count", 0);
        String fileName = String.format(Locale.US, "IMG_%04d.jpg", imgCount);
        prefs.edit().putInt("img_count", imgCount + 1).apply();
        String appName = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/" + appName);
        try {
            android.net.Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            if (uri != null) {
                try (OutputStream out = context.getContentResolver().openOutputStream(uri)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out); }
                
                try (android.os.ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "rw")) {
                    if (pfd != null) {
                        ExifInterface exif = new ExifInterface(pfd.getFileDescriptor());
                        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL);
                        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER);
                        
                        if (camera != null) {
                            try {
                                Camera2CameraInfo c2Info = Camera2CameraInfo.from(camera.getCameraInfo());
                                float[] focals = c2Info.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                                if (focals != null && focals.length > 0) exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, String.valueOf(focals[0]));
                                float[] apertures = c2Info.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
                                if (apertures != null && apertures.length > 0) exif.setAttribute(ExifInterface.TAG_F_NUMBER, String.valueOf(apertures[0]));
                            } catch (Exception ignored) {}
                        }

                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED 
                                && prefs.getBoolean("pref_location", false)) {
                            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                                if (location != null) {
                                    try (android.os.ParcelFileDescriptor pfdLoc = context.getContentResolver().openFileDescriptor(uri, "rw")) {
                                        if (pfdLoc != null) {
                                            ExifInterface exifLoc = new ExifInterface(pfdLoc.getFileDescriptor());
                                            exifLoc.setLatLong(location.getLatitude(), location.getLongitude());
                                            exifLoc.saveAttributes();
                                        }
                                    } catch (Exception ignored) {}
                                }
                            });
                        }
                        exif.saveAttributes();
                    }
                } catch (Exception ignored) {}
                Toast.makeText(context, "Photo saved", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) { Log.e(TAG, "Failed to save photo", e); } finally { notifyState(CameraState.IDLE); }
    }

    @android.annotation.SuppressLint("MissingPermission")
    public void startRecording() {
        if (currentMode != CaptureMode.VIDEO || videoCapture == null) return;
        
        SharedPreferences prefs = context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE);
        int vidCount = prefs.getInt("vid_count", 0);
        String name = String.format(Locale.US, "VID_%04d", vidCount);
        prefs.edit().putInt("vid_count", vidCount + 1).apply();

        String appName = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
        ContentValues cv = new ContentValues(); 
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name); 
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cv.put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/" + appName);
        } else {
            cv.put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera");
        }

        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(context.getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(cv).build();
        activeRecording = videoCapture.getOutput().prepareRecording(context, options).withAudioEnabled().start(ContextCompat.getMainExecutor(context), event -> {
            if (event instanceof VideoRecordEvent.Start) notifyState(CameraState.RECORDING);
            else if (event instanceof VideoRecordEvent.Finalize) {
                if (!((VideoRecordEvent.Finalize) event).hasError()) Toast.makeText(context, "Video saved", Toast.LENGTH_SHORT).show();
                notifyState(CameraState.IDLE);
            }
        });
    }

    public void stopRecording() { if (activeRecording != null) { activeRecording.stop(); activeRecording = null; } }

    public void focusOnArea(float x, float y, int size) {
        if (camera == null) return;
        androidx.camera.core.MeteringPoint point = previewView.getMeteringPointFactory().createPoint(x, y, size / (float)previewView.getWidth());
        camera.getCameraControl().startFocusAndMetering(new androidx.camera.core.FocusMeteringAction.Builder(point).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build());
    }

    public void setCurrentLut(String lutFile) { this.currentSelectedLutFile = lutFile; }
}
