package com.example.myapplication;

import android.content.Intent;
import android.provider.MediaStore;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.PopupMenu;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import android.view.ViewGroup;
import android.graphics.Color;
import android.content.SharedPreferences;

import android.view.OrientationEventListener;
import android.view.MotionEvent;
import android.animation.ObjectAnimator;
import android.view.animation.OvershootInterpolator;
import android.view.ScaleGestureDetector;
import android.view.GestureDetector;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.PopupWindow;
import android.view.LayoutInflater;
import android.view.Gravity;

import androidx.annotation.NonNull;
import android.graphics.Paint;

public class MainActivity extends AppCompatActivity implements CameraUiController {

    private LinearLayout slidingPanel;
    private ImageView btnToggle;
    private ImageView btnFlash;
    private boolean isPanelExpanded = false;

    private RecyclerView rvLuts;
    private LutAdapter lutAdapter;
    private List<LutItem> lutItems;

    private RecyclerView rvModes;
    private ModeAdapter modeAdapter;
    private List<String> modes;

    private CommandInvoker commandInvoker;
    private CameraSettingsStore settingsStore;
    private CameraManager cameraManager;

    private LinearLayout layoutEvSlider;
    private ExposureSlider exposureSlider;
    private TextView tvAspectRatio;
    private TextView tvResolution;
    private TextView tvFps;

    private View gridOverlay;
    private SharedPreferences prefs;

    private OrientationEventListener orientationEventListener;
    private int currentRotation = 0;

    private final ExecutorService hardwareExecutor = Executors.newSingleThreadExecutor();

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private float currentZoomRatio = 1.0f;
    private float minZoomRatio = 0.5f;
    private float maxZoomRatio = 5.0f;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (Boolean isGranted : permissions.values()) {
                    if (!isGranted) allGranted = false;
                }
                if (allGranted) {
                    startCamera();
                    hardwareExecutor.execute(() -> {
                        HardwareInfo.getInstance().scanHardware(this);
                        runOnUiThread(() -> {
                            setupZoomPanel();
                            setupExposureSlider();
                        });
                    });
                } else {
                    Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                }
            });

    private TrackingOverlayView trackingOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_basic_mode);
        
        trackingOverlay = findViewById(R.id.trackingOverlay);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topBar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomSection), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
                int rotation = 0;
                if (orientation >= 45 && orientation < 135) rotation = 270;
                else if (orientation >= 135 && orientation < 225) rotation = 180;
                else if (orientation >= 225 && orientation < 315) rotation = 90;

                if (rotation != currentRotation) {
                    currentRotation = rotation;
                    rotateIcons(rotation);
                }
            }
        };

        slidingPanel = findViewById(R.id.slidingPanel);
        setupLutSelector();
        btnToggle = findViewById(R.id.btnToggle);
        btnFlash = findViewById(R.id.btnFlash);
        layoutEvSlider = findViewById(R.id.layoutEvSlider);
        exposureSlider = findViewById(R.id.exposureSlider);
        gridOverlay = findViewById(R.id.gridOverlay);
        prefs = getSharedPreferences("camera_prefs", MODE_PRIVATE);

        View topBar = findViewById(R.id.topBar);
        if (topBar != null) {
            tvAspectRatio = topBar.findViewById(R.id.tvAspectRatio);
            tvResolution = topBar.findViewById(R.id.tvResolution);
            tvFps = topBar.findViewById(R.id.tvFps);
        }

        settingsStore = new CameraSettingsStore();
        View viewFinder = findViewById(R.id.viewFinder);
        setupGestures(viewFinder);

        cameraManager = new CameraManager(this, viewFinder);
        cameraManager.setTrackingListener(new CameraManager.TrackingListener() {
            @Override
            public void onObjectDetected(android.graphics.Rect boundingBox, boolean isEye) {
                runOnUiThread(() -> {
                    if (trackingOverlay != null) {
                        trackingOverlay.updateBoundingBox(boundingBox, isEye);
                    }
                });
            }

            @Override
            public void onClearTracking() {
                runOnUiThread(() -> {
                    if (trackingOverlay != null) {
                        trackingOverlay.clear();
                    }
                });
            }
        });
        commandInvoker = new CommandInvoker(cameraManager, settingsStore);

        List<String> permissionsToRequest = new ArrayList<>();
        permissionsToRequest.add(Manifest.permission.CAMERA);
        permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (allPermissionsGranted(permissionsToRequest)) {
            startCamera();
            hardwareExecutor.execute(() -> {
                HardwareInfo.getInstance().scanHardware(this);
                runOnUiThread(() -> {
                    setupZoomPanel();
                    setupExposureSlider();
                });
            });
        } else {
            requestPermissionsLauncher.launch(permissionsToRequest.toArray(new String[0]));
        }

        btnToggle.setOnClickListener(v -> {
            isPanelExpanded = !isPanelExpanded;
            slidingPanel.setVisibility(isPanelExpanded ? View.VISIBLE : View.GONE);
            btnToggle.setImageResource(isPanelExpanded ? R.drawable.ic_arrow_up : R.drawable.ic_arrow_down);
        });

        findViewById(R.id.btnShutter).setOnClickListener(v -> onShutterClicked());
        findViewById(R.id.btnSwitch).setOnClickListener(v -> onSwitchCameraClicked());
        findViewById(R.id.btnAlbum).setOnClickListener(v -> openGallery());
        if (btnFlash != null) {
            btnFlash.setOnClickListener(v -> {
                boolean current = settingsStore.isFlashEnabled();
                commandInvoker.updateConfiguration(ConfigType.FLASH, !current);
                updateFlashIcon();
            });
            updateFlashIcon();
        }

        applyClickAnimation(findViewById(R.id.btnShutter));
        applyClickAnimation(findViewById(R.id.btnSwitch));
        applyClickAnimation(btnToggle);
        applyClickAnimation(findViewById(R.id.btnAlbum));
        applyClickAnimation(findViewById(R.id.btnFlash));

        if (topBar != null) {
            View resBtn = topBar.findViewById(R.id.tvResolution);
            View fpsBtn = topBar.findViewById(R.id.tvFps);
            View evBtn = topBar.findViewById(R.id.tvEv);
            View menuBtn = topBar.findViewById(R.id.btnMenu);

            applyClickAnimation(resBtn);
            applyClickAnimation(fpsBtn);
            applyClickAnimation(evBtn);
            applyClickAnimation(tvAspectRatio);
            applyClickAnimation(tvResolution);
            applyClickAnimation(tvFps);
            applyClickAnimation(menuBtn);

            menuBtn.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingActivity.class)));
            evBtn.setOnClickListener(v -> {
                if (layoutEvSlider != null) {
                    boolean isVisible = layoutEvSlider.getVisibility() == View.VISIBLE;
                    layoutEvSlider.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                    updateEvControlStyle(!isVisible);
                }
            });
            tvAspectRatio.setOnClickListener(this::showAspectRatioPopup);
            tvResolution.setOnClickListener(v -> {
                if (settingsStore.getCaptureMode() == CaptureMode.PHOTO) {
                    if (HardwareInfo.getInstance().maxPhotoResolutionMp > 12) {
                        showResolutionPopup(v);
                    } else {
                        Toast.makeText(this, "Camera only supports 12mp", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    showVideoConfigPopup(v);
                }
            });
            tvFps.setOnClickListener(v -> showVideoConfigPopup(v));
        }

        setupModeSwitcher();
        findViewById(R.id.btnEvClose).setOnClickListener(v -> {
            layoutEvSlider.setVisibility(View.GONE);
            updateEvControlStyle(false);
        });
    }

    private void setupExposureSlider() {
        if (exposureSlider == null) return;
        exposureSlider.setProgress(0); 
        exposureSlider.setOnValueChangeListener((index, ev) -> commandInvoker.updateConfiguration(ConfigType.EV, ev));
    }

    private void showAspectRatioPopup(View v) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_aspect_ratio, null);
        PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        TextView tv43 = popupView.findViewById(R.id.tvRatio43);
        TextView tv169 = popupView.findViewById(R.id.tvRatio169);
        TextView tv11 = popupView.findViewById(R.id.tvRatio11);

        String current = settingsStore.getCurrentAspectRatio();
        if ("4:3".equals(current)) tv43.setAlpha(1.0f);
        else if ("16:9".equals(current)) tv169.setAlpha(1.0f);
        else if ("1:1".equals(current)) tv11.setAlpha(1.0f);

        View.OnClickListener listener = view -> {
            String ratio = ((TextView) view).getText().toString();
            setAspectRatio(ratio);
            popupWindow.dismiss();
        };

        tv43.setOnClickListener(listener);
        tv169.setOnClickListener(listener);
        tv11.setOnClickListener(listener);

        popupWindow.showAsDropDown(v, 0, 10);
    }

    private void showResolutionPopup(View v) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_resolution, null);
        PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        TextView tv48 = popupView.findViewById(R.id.tvRes48);
        TextView tv24 = popupView.findViewById(R.id.tvRes24);
        TextView tv12 = popupView.findViewById(R.id.tvRes12);

        List<Integer> supported = HardwareInfo.getInstance().supportedMpList;
        tv48.setVisibility(supported.contains(48) ? View.VISIBLE : View.GONE);
        tv24.setVisibility(supported.contains(24) ? View.VISIBLE : View.GONE);

        String current = settingsStore.getCurrentResolution();
        if ("48mp".equals(current)) tv48.setAlpha(1.0f);
        else if ("24mp".equals(current)) tv24.setAlpha(1.0f);
        else if ("12mp".equals(current)) tv12.setAlpha(1.0f);

        View.OnClickListener listener = view -> {
            String res = ((TextView) view).getText().toString();
            tvResolution.setText(res);
            commandInvoker.updateConfiguration(ConfigType.RESOLUTION, res);
            popupWindow.dismiss();
        };

        tv48.setOnClickListener(listener);
        tv24.setOnClickListener(listener);
        tv12.setOnClickListener(listener);

        popupWindow.showAsDropDown(v, 0, 10);
    }

    private void showVideoConfigPopup(View v) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_video_config, null);
        PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        TextView tv4K = popupView.findViewById(R.id.tvVideo4K);
        TextView tvHD = popupView.findViewById(R.id.tvVideoHD);
        TextView tv720 = popupView.findViewById(R.id.tvVideo720);
        TextView tvFps120 = popupView.findViewById(R.id.tvFps120);
        TextView tvFps60 = popupView.findViewById(R.id.tvFps60);
        TextView tvFps30 = popupView.findViewById(R.id.tvFps30);
        
        HardwareInfo hardware = HardwareInfo.getInstance();
        final String[] activeQual = {settingsStore.getCurrentVideoQuality()};
        final int[] activeFps = {settingsStore.getCurrentFps()};

        // Helper to update selection visuals
        Runnable updateVisuals = () -> {
            // Quality
            tv4K.setAlpha("4K".equals(activeQual[0]) ? 1.0f : 0.5f);
            tvHD.setAlpha("HD".equals(activeQual[0]) ? 1.0f : 0.5f);
            tv720.setAlpha("720".equals(activeQual[0]) ? 1.0f : 0.5f);

            // FPS based on current resolution
            HardwareInfo.VideoCapability cap = null;
            for (HardwareInfo.VideoCapability c : hardware.videoCapabilities) {
                if (c.qualityLabel.equals(activeQual[0])) {
                    cap = c;
                    break;
                }
            }

            if (cap != null) {
                tvFps120.setEnabled(cap.supportedFps.contains(120));
                tvFps120.setAlpha(tvFps120.isEnabled() ? (activeFps[0] == 120 ? 1.0f : 0.5f) : 0.1f);

                tvFps60.setEnabled(cap.supportedFps.contains(60));
                tvFps60.setAlpha(tvFps60.isEnabled() ? (activeFps[0] == 60 ? 1.0f : 0.5f) : 0.1f);

                tvFps30.setEnabled(cap.supportedFps.contains(30));
                tvFps30.setAlpha(tvFps30.isEnabled() ? (activeFps[0] == 30 ? 1.0f : 0.5f) : 0.1f);
            }
        };

        // Accessibility checks
        boolean s4k = false, sHD = false, s720 = false;
        for (HardwareInfo.VideoCapability c : hardware.videoCapabilities) {
            if (!c.supportedFps.isEmpty()) {
                if ("4K".equals(c.qualityLabel)) s4k = true;
                if ("HD".equals(c.qualityLabel)) sHD = true;
                if ("720".equals(c.qualityLabel)) s720 = true;
            }
        }
        tv4K.setVisibility(s4k ? View.VISIBLE : View.GONE);
        tvHD.setVisibility(sHD ? View.VISIBLE : View.GONE);
        tv720.setVisibility(s720 ? View.VISIBLE : View.GONE);

        updateVisuals.run();

        // Listeners
        View.OnClickListener qualListener = view -> {
            String newQual = "HD";
            if (view.getId() == R.id.tvVideo4K) newQual = "4K";
            else if (view.getId() == R.id.tvVideo720) newQual = "720";

            activeQual[0] = newQual;
            commandInvoker.updateConfiguration(ConfigType.VIDEO_QUALITY, newQual);
            tvResolution.setText(newQual);

            // Check if current active FPS is still valid for this quality
            validateFpsForQuality(newQual);
            activeFps[0] = settingsStore.getCurrentFps();
            updateVisuals.run();
        };

        tv4K.setOnClickListener(qualListener);
        tvHD.setOnClickListener(qualListener);
        tv720.setOnClickListener(qualListener);

        View.OnClickListener fpsListener = view -> {
            int newFps = 30;
            if (view.getId() == R.id.tvFps120) newFps = 120;
            else if (view.getId() == R.id.tvFps60) newFps = 60;

            activeFps[0] = newFps;
            commandInvoker.updateConfiguration(ConfigType.FPS, newFps);
            tvFps.setText(String.valueOf(newFps));
            updateVisuals.run();
        };

        tvFps120.setOnClickListener(fpsListener);
        tvFps60.setOnClickListener(fpsListener);
        tvFps30.setOnClickListener(fpsListener);

        popupWindow.showAsDropDown(v, 0, 10);
    }

    private void validateFpsForQuality(String quality) {
        HardwareInfo hardware = HardwareInfo.getInstance();
        for (HardwareInfo.VideoCapability c : hardware.videoCapabilities) {
            if (c.qualityLabel.equals(quality)) {
                if (!c.supportedFps.contains(settingsStore.getCurrentFps())) {
                    int fallback = c.supportedFps.isEmpty() ? 30 : c.supportedFps.get(0);
                    commandInvoker.updateConfiguration(ConfigType.FPS, fallback);
                    tvFps.setText(String.valueOf(fallback));
                }
                break;
            }
        }
    }

    private void setAspectRatio(String ratioLabel) {
        if (tvAspectRatio != null) tvAspectRatio.setText(ratioLabel);
        String dimensionRatio;
        switch (ratioLabel) {
            case "4:3": dimensionRatio = "3:4"; break;
            case "16:9": dimensionRatio = "9:16"; break;
            case "1:1": dimensionRatio = "1:1"; break;
            default: dimensionRatio = "3:4"; break;
        }

        View viewFinder = findViewById(R.id.viewFinder);
        if (viewFinder != null) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) viewFinder.getLayoutParams();
            params.dimensionRatio = dimensionRatio;
            viewFinder.setLayoutParams(params);
        }
        if (gridOverlay != null) {
            ConstraintLayout.LayoutParams gridParams = (ConstraintLayout.LayoutParams) gridOverlay.getLayoutParams();
            gridParams.dimensionRatio = dimensionRatio;
            gridOverlay.setLayoutParams(gridParams);
        }
        commandInvoker.updateConfiguration(ConfigType.ASPECT_RATIO, ratioLabel);
    }

    private void setupModeSwitcher() {
        rvModes = findViewById(R.id.rvModes);
        modes = Arrays.asList("Video", "Photo");
        modeAdapter = new ModeAdapter(modes, (mode, position) -> {
            boolean isVideo = mode.equals("Video");
            if (isVideo && settingsStore.getCaptureMode() == CaptureMode.VIDEO) return;
            if (!isVideo && settingsStore.getCaptureMode() == CaptureMode.PHOTO) return;

            onModeChanged(isVideo);
            modeAdapter.setSelectedPosition(position);
            rvModes.smoothScrollToPosition(position);
        });

        rvModes.setAdapter(modeAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvModes.setLayoutManager(layoutManager);

        LinearSnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(rvModes);

        rvModes.post(() -> {
            int initialPosition = settingsStore.getCaptureMode() == CaptureMode.VIDEO ? 0 : 1;
            modeAdapter.setSelectedPosition(initialPosition);
            rvModes.scrollToPosition(initialPosition);
            rvModes.post(() -> {
                View view = layoutManager.findViewByPosition(initialPosition);
                if (view != null) {
                    int padding = (rvModes.getWidth() / 2) - (view.getWidth() / 2);
                    layoutManager.scrollToPositionWithOffset(initialPosition, padding);
                }
            });
        });

        rvModes.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View centerView = snapHelper.findSnapView(layoutManager);
                    if (centerView != null) {
                        int pos = layoutManager.getPosition(centerView);
                        if (pos != RecyclerView.NO_POSITION && pos != modeAdapter.getSelectedPosition()) {
                            onModeChanged(modes.get(pos).equals("Video"));
                            modeAdapter.setSelectedPosition(pos);
                        }
                    }
                }
            }
        });
    }

    private void updateModeUi(boolean isVideo) {
        modeAdapter.setSelectedPosition(isVideo ? 0 : 1);

        if (isVideo) {
            if (tvResolution != null) {
                tvResolution.setText(settingsStore.getCurrentVideoQuality());
                tvResolution.setTextColor(Color.parseColor("#FF9800"));
            }

            if (tvFps != null) {
                tvFps.setVisibility(View.VISIBLE);
                tvFps.setText(String.valueOf(settingsStore.getCurrentFps()));
                tvFps.setTextColor(Color.parseColor("#FF9800"));
            }

            if (tvAspectRatio != null) {
                tvAspectRatio.setVisibility(View.VISIBLE);
                tvAspectRatio.setText(settingsStore.getCurrentAspectRatio());
                tvAspectRatio.setTextColor(Color.WHITE);
            }
        } else {

            if (tvResolution != null) {
                tvResolution.setText(settingsStore.getCurrentResolution());
                tvResolution.setTextColor(Color.parseColor("#FF9800"));
            }

            if (tvFps != null) {
                tvFps.setVisibility(View.GONE);
            }

            if (tvAspectRatio != null) {
                tvAspectRatio.setVisibility(View.VISIBLE);
                tvAspectRatio.setText(settingsStore.getCurrentAspectRatio());
                tvAspectRatio.setTextColor(Color.WHITE);
            }
        }
    }

    private void animateModeSwitch(View container, View target) {
        if (container == null || target == null) return;
        float containerWidth = container.getWidth();
        float targetCenterX = target.getLeft() + (target.getWidth() / 2f);
        float offset = (containerWidth / 2f) - targetCenterX;

        ObjectAnimator anim = ObjectAnimator.ofFloat(container, "translationX", offset);
        anim.setDuration(250);
        anim.setInterpolator(new OvershootInterpolator(0.8f));
        anim.start();
    }

    private void rotateIcons(int degrees) {
        rotateView(findViewById(R.id.btnSwitch), degrees);
        rotateView(btnFlash, degrees);
        rotateView(findViewById(R.id.btnToggle), degrees);
        rotateView(findViewById(R.id.btnAlbum), degrees);
        LinearLayout layoutZoom = findViewById(R.id.layoutZoom);
        if (layoutZoom != null) {
            for (int i = 0; i < layoutZoom.getChildCount(); i++) rotateView(layoutZoom.getChildAt(i), (float)degrees);
        }
        View topBar = findViewById(R.id.topBar);
        if (topBar != null) {
            rotateView(topBar.findViewById(R.id.tvResolution), (float)degrees);
            rotateView(topBar.findViewById(R.id.tvFps), (float)degrees);
            rotateView(topBar.findViewById(R.id.tvEv), (float)degrees);
            rotateView(topBar.findViewById(R.id.tvAspectRatio), (float)degrees);
            rotateView(topBar.findViewById(R.id.btnMenu), (float)degrees);
        }
    }

    private void rotateView(View v, float degrees) {
        if (v != null) v.animate().rotation(degrees).setDuration(300).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gridOverlay != null) gridOverlay.setVisibility(prefs.getBoolean("pref_grid", false) ? View.VISIBLE : View.GONE);
        if (orientationEventListener != null && orientationEventListener.canDetectOrientation()) orientationEventListener.enable();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (orientationEventListener != null) orientationEventListener.disable();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(intent); } catch (Exception e) { Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show(); }
    }

    private boolean allPermissionsGranted(List<String> permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void startCamera() {
        String startMode = prefs.getString("pref_start_mode", "CĐ chụp trước");
        if ("CĐ chụp trước".equals(startMode)) {
            // Restore saved settings
            String savedLut = prefs.getString("pref_current_lut", null);
            cameraManager.setCurrentLut(savedLut);
            
            String savedRes = prefs.getString("pref_resolution", "12mp");
            commandInvoker.updateConfiguration(ConfigType.RESOLUTION, savedRes);
            
            String savedRatio = prefs.getString("pref_aspect_ratio", "4:3");
            setAspectRatio(savedRatio);
            
            String savedVQual = prefs.getString("pref_video_quality", "HD");
            int savedFps = prefs.getInt("pref_video_fps", 60);
            cameraManager.updateVideoConfig(savedVQual, savedFps);

            CaptureMode savedMode = CaptureMode.PHOTO;
            try {
                savedMode = CaptureMode.valueOf(prefs.getString("pref_capture_mode", "PHOTO"));
            } catch (Exception e) {
                // Fallback to PHOTO
            }
            commandInvoker.setCaptureMode(savedMode);
            updateModeUi(savedMode == CaptureMode.VIDEO);
        } else {
            commandInvoker.setCaptureMode(CaptureMode.PHOTO);
            updateModeUi(false);
        }
    }

    @Override
    public void onShutterClicked() {
        if (settingsStore.getCaptureMode() == CaptureMode.PHOTO) commandInvoker.takePhoto();
        else if (settingsStore.getCameraState() == CameraState.RECORDING) commandInvoker.stopRecording();
        else commandInvoker.startRecording();
    }

    @Override
    public void onSwitchCameraClicked() { commandInvoker.switchCamera(); }

    @Override
    public void onZoomSelected(float zoomLevel) { commandInvoker.setZoomRatio(zoomLevel); }

    @Override
    public void onModeChanged(boolean isVideoMode) {
        CaptureMode mode = isVideoMode ? CaptureMode.VIDEO : CaptureMode.PHOTO;
        commandInvoker.setCaptureMode(mode);
        prefs.edit().putString("pref_capture_mode", mode.name()).apply();
    }

    @Override
    public void onPresetSelected(int presetIndex) { commandInvoker.applyPreset(presetIndex); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hardwareExecutor.shutdown();
    }

    private void setupZoomPanel() {
        LinearLayout layoutZoom = findViewById(R.id.layoutZoom);
        if (layoutZoom == null) return;
        layoutZoom.removeAllViews();
        HardwareInfo info = HardwareInfo.getInstance();
        for (Float ratio : info.zoomRatios) {
            TextView btnZoom = new TextView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int) (44 * getResources().getDisplayMetrics().density), (int) (44 * getResources().getDisplayMetrics().density));
            params.setMarginEnd((int) (12 * getResources().getDisplayMetrics().density));
            btnZoom.setLayoutParams(params);
            btnZoom.setBackgroundResource(R.drawable.bg_circle_border);
            btnZoom.setGravity(android.view.Gravity.CENTER);
            btnZoom.setText(ratio + "x");
            btnZoom.setTextColor(Color.WHITE);
            btnZoom.setTextSize(13f);
            btnZoom.setRotation((float)currentRotation);
            applyClickAnimation(btnZoom);
            btnZoom.setOnClickListener(v -> { onZoomSelected(ratio); currentZoomRatio = ratio; });
            layoutZoom.addView(btnZoom);
        }
    }

    private void applyClickAnimation(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: v.animate().scaleX(0.9f).scaleY(0.9f).alpha(0.7f).setDuration(100).start(); break;
                case MotionEvent.ACTION_UP: v.performClick();
                case MotionEvent.ACTION_CANCEL: v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(100).start(); break;
            }
            return true;
        });
    }

    private void updateFlashIcon() { if (btnFlash != null) btnFlash.setSelected(settingsStore.isFlashEnabled()); }

    private void setupGestures(View viewFinder) {
        if (viewFinder == null) return;
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                currentZoomRatio = Math.max(minZoomRatio, Math.min(currentZoomRatio * detector.getScaleFactor(), maxZoomRatio));
                commandInvoker.setZoomRatio(currentZoomRatio);
                return true;
            }
        });
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 200) {
                    int currentPosition = modeAdapter.getSelectedPosition();
                    if (diffX > 0 && currentPosition == 1) { // Fling right to Video
                        runOnUiThread(() -> rvModes.findViewHolderForAdapterPosition(0).itemView.performClick());
                        return true;
                    } else if (diffX < 0 && currentPosition == 0) { // Fling left to Photo
                        runOnUiThread(() -> rvModes.findViewHolderForAdapterPosition(1).itemView.performClick());
                        return true;
                    }
                }
                return false;
            }
        });
        viewFinder.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float x = event.getX();
                float y = event.getY();
                
                String touchFunc = prefs.getString("pref_touch_func", "Dò tìm đối tượng");
                if (touchFunc.contains("Dò tìm")) {
                    handleObjectTracking(x, y);
                } else {
                    // Normal focus
                    v.performClick();
                }
            }
            return true;
        });
    }

    private void handleObjectTracking(float x, float y) {
        if (trackingOverlay != null) {
            trackingOverlay.showTrackingBox(x, y);
        }
        
        // Force focus and start manual tracking
        cameraManager.triggerManualTracking(x, y);
    }

    private void setupLutSelector() {
        rvLuts = findViewById(R.id.rvLuts);
        lutItems = new ArrayList<>();

        // "Gốc" - Original
        lutItems.add(new LutItem("Gốc", null, null));

        // Define some ColorMatrices for preview (simplified approximations of the LUTs)
        ColorMatrix fujiMatrix = new ColorMatrix();
        fujiMatrix.setSaturation(1.2f);

        ColorMatrix bwMatrix = new ColorMatrix();
        bwMatrix.setSaturation(0f);

        ColorMatrix retroMatrix = new ColorMatrix();
        retroMatrix.set(new float[] {
            1.2f, 0.0f, 0.0f, 0.0f, 20.0f,
            0.0f, 1.0f, 0.0f, 0.0f, 5.0f,
            0.0f, 0.0f, 0.8f, 0.0f, -10.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        });

        ColorMatrix cyberpunkMatrix = new ColorMatrix();
        cyberpunkMatrix.set(new float[] {
            1.0f, 0.0f, 0.0f, 0.0f, 30.0f,
            0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.2f, 0.0f, 50.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        });

        lutItems.add(new LutItem("Fuji C400", "fuji_c400.PNG", fujiMatrix));
        lutItems.add(new LutItem("Classic Chrome", "classic_chrome.PNG", null));
        lutItems.add(new LutItem("Cyberpunk", "cyberpunk_800t.PNG", cyberpunkMatrix));
        lutItems.add(new LutItem("Retro", "retro_1.PNG", retroMatrix));
        lutItems.add(new LutItem("Monochrome", "leica_monochrome.PNG", bwMatrix));
        lutItems.add(new LutItem("Backrooms", "backrooms.PNG", null));
        lutItems.add(new LutItem("Kodacolor", "kodacolor_100.PNG", null));
        lutItems.add(new LutItem("Superia", "fuji_superia_400.PNG", null));

        lutAdapter = new LutAdapter(lutItems, (item, position) -> {
            applyLut(item);
            rvLuts.smoothScrollToPosition(position);
        });

        rvLuts.setAdapter(lutAdapter);

        LinearSnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(rvLuts);

        rvLuts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View centerView = snapHelper.findSnapView(rvLuts.getLayoutManager());
                    if (centerView != null) {
                        int pos = rvLuts.getChildAdapterPosition(centerView);
                        if (pos != RecyclerView.NO_POSITION) {
                            lutAdapter.setSelectedPosition(pos);
                            applyLut(lutItems.get(pos));
                        }
                    }
                }
            }
        });
    }

    private void applyLut(LutItem item) {
        String fileName = item.getFileName();
        cameraManager.setCurrentLut(fileName);
        prefs.edit().putString("pref_current_lut", fileName).apply();

        PreviewView previewView = findViewById(R.id.viewFinder);
        if (previewView != null) {
            if (item.getColorMatrix() != null) {
                Paint paint = new Paint();
                paint.setColorFilter(new ColorMatrixColorFilter(item.getColorMatrix()));
                previewView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
            } else {
                previewView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        }
    }

    private void updateEvControlStyle(boolean isPanelVisible) {
        View evBtn = findViewById(R.id.tvEv);
        if (evBtn instanceof TextView) {
            TextView tv = (TextView) evBtn;
            if (isPanelVisible) {
                tv.setBackgroundResource(R.drawable.bg_rounded_orange);
                tv.setTextColor(Color.WHITE);
            } else {
                tv.setBackground(null);
                tv.setTextColor(Color.parseColor("#FF9800"));
            }
        }
    }
}
