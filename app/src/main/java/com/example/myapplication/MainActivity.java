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
import java.util.Map;

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
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import android.graphics.Paint;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.content.Context;

public class MainActivity extends AppCompatActivity implements CameraUiController, CameraManager.StateCallback {

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

    private RecyclerView rvZoom;
    private ZoomAdapter zoomAdapter;
    private List<Float> zoomRatios;

    private CameraSettingsStore settingsStore;
    private CameraManager cameraManager;

    private LinearLayout layoutEvSlider;
    private ExposureSlider exposureSlider;
    private TextView tvAspectRatio;
    private TextView tvResolution;
    private TextView tvFps;
    private TextView tvEv;

    private View gridOverlay;
    private SharedPreferences prefs;

    private OrientationEventListener orientationEventListener;
    private int currentRotation = 0;

    private final ExecutorService hardwareExecutor = Executors.newSingleThreadExecutor();

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private float currentZoomRatio = 1.0f;
    private float minZoomRatio = 0.5f;
    private float maxZoomRatio = 10.0f;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                    if (!entry.getValue()) {
                        // Camera and Audio are mandatory for basic func
                        if (entry.getKey().equals(Manifest.permission.CAMERA) || entry.getKey().equals(Manifest.permission.RECORD_AUDIO)) {
                            allGranted = false;
                        }
                    } else {
                        // If location granted here, set pref to true
                        if (entry.getKey().equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                            prefs.edit().putBoolean("pref_location", true).apply();
                        }
                    }
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
                    Toast.makeText(this, "Camera and Audio permissions are required.", Toast.LENGTH_SHORT).show();
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
            tvEv = topBar.findViewById(R.id.tvEv);
        }

        settingsStore = new CameraSettingsStore();
        View viewFinder = findViewById(R.id.viewFinder);
        setupGestures(viewFinder);

        setupModeSwitcher();

        cameraManager = new CameraManager(this, viewFinder);
        cameraManager.setStateCallback(this);
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

        List<String> permissionsToRequest = new ArrayList<>();
        permissionsToRequest.add(Manifest.permission.CAMERA);
        permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
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
                updateConfiguration(ConfigType.FLASH, !current);
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
            View menuBtn = topBar.findViewById(R.id.btnMenu);

            applyClickAnimation(resBtn);
            applyClickAnimation(fpsBtn);
            applyClickAnimation(tvEv);
            applyClickAnimation(tvAspectRatio);
            applyClickAnimation(tvResolution);
            applyClickAnimation(tvFps);
            applyClickAnimation(menuBtn);

            menuBtn.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingActivity.class)));
            tvEv.setOnClickListener(v -> {
                if (layoutEvSlider != null) {
                    boolean isVisible = layoutEvSlider.getVisibility() == View.VISIBLE;
                    layoutEvSlider.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                    updateEvControlStyle(!isVisible);
                }
            });
            tvAspectRatio.setOnClickListener(this::showAspectRatioPopup);
            tvResolution.setOnClickListener(v -> {
                if (settingsStore.getCaptureMode() == CaptureMode.PHOTO) {
                    showResolutionPopup(v);
                } else {
                    showVideoConfigPopup(v);
                }
            });
            tvFps.setOnClickListener(v -> showVideoConfigPopup(v));
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

        findViewById(R.id.btnEvClose).setOnClickListener(v -> {
            layoutEvSlider.setVisibility(View.GONE);
            updateEvControlStyle(false);
        });
    }

    private void setupExposureSlider() {
        if (exposureSlider == null) return;
        exposureSlider.setProgress(0); 
        exposureSlider.setOnValueChangeListener((index, ev) -> {
            triggerVibration();
            updateConfiguration(ConfigType.EV, ev);
        });
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

        RadioButton rb48 = popupView.findViewById(R.id.tvRes48);
        RadioButton rb24 = popupView.findViewById(R.id.tvRes24);
        RadioButton rb12 = popupView.findViewById(R.id.tvRes12);
        
        popupView.findViewById(R.id.btnClosePopup).setOnClickListener(view -> popupWindow.dismiss());

        List<Integer> supported = HardwareInfo.getInstance().supportedMpList;
        String current = settingsStore.getCurrentResolution();

        rb48.setVisibility(supported.contains(48) ? View.VISIBLE : View.GONE);
        rb48.setChecked("48mp".equals(current));

        rb24.setVisibility(supported.contains(24) ? View.VISIBLE : View.GONE);
        rb24.setChecked("24mp".equals(current));

        rb12.setVisibility(View.VISIBLE);
        rb12.setChecked("12mp".equals(current));

        RadioGroup.OnCheckedChangeListener listener = (group, checkedId) -> {
            RadioButton rb = popupView.findViewById(checkedId);
            if (rb == null || !rb.isEnabled()) return;
            String res = rb.getText().toString();
            tvResolution.setText(res);
            updateConfiguration(ConfigType.RESOLUTION, res);
            prefs.edit().putString("pref_resolution", res).apply();
            popupWindow.dismiss();
        };

        ((RadioGroup)popupView.findViewById(R.id.rgResolution)).setOnCheckedChangeListener(listener);

        popupWindow.showAsDropDown(v, 0, 10);
    }

    private void showVideoConfigPopup(View v) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_video_config, null);
        PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        RadioButton rb4K = popupView.findViewById(R.id.tvVideo4K);
        RadioButton rbHD = popupView.findViewById(R.id.tvVideoHD);
        RadioButton rb720 = popupView.findViewById(R.id.tvVideo720);
        RadioButton rb120 = popupView.findViewById(R.id.tvFps120);
        RadioButton rb60 = popupView.findViewById(R.id.tvFps60);
        RadioButton rb30 = popupView.findViewById(R.id.tvFps30);
        
        popupView.findViewById(R.id.btnClosePopupVideo).setOnClickListener(view -> popupWindow.dismiss());
        
        HardwareInfo hardware = HardwareInfo.getInstance();
        final String currentQual = settingsStore.getCurrentVideoQuality();
        final int currentFps = settingsStore.getCurrentFps();

        // Update Quality Options
        updateQualityRB(rb4K, "4K", currentQual, hardware);
        updateQualityRB(rbHD, "HD", currentQual, hardware);
        updateQualityRB(rb720, "720", currentQual, hardware);

        // Update FPS Options based on current Quality
        HardwareInfo.VideoCapability cap = null;
        for (HardwareInfo.VideoCapability c : hardware.videoCapabilities) { if (c.qualityLabel.equals(currentQual)) { cap = c; break; } }
        if (cap != null) {
            updateFpsRB(rb120, 120, currentFps, cap);
            updateFpsRB(rb60, 60, currentFps, cap);
            updateFpsRB(rb30, 30, currentFps, cap);
        }

        ((RadioGroup)popupView.findViewById(R.id.rgQuality)).setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton rb = popupView.findViewById(checkedId);
            if (rb == null) return;
            String newQual = rb.getText().toString().replace("p", "");
            updateConfiguration(ConfigType.VIDEO_QUALITY, newQual);
            tvResolution.setText(newQual);
            validateFpsForQuality(newQual);
            popupWindow.dismiss();
            showVideoConfigPopup(v); // Re-show to refresh FPS options if user wants, or just dismiss
        });

        ((RadioGroup)popupView.findViewById(R.id.rgFps)).setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton rb = popupView.findViewById(checkedId);
            if (rb == null || !rb.isEnabled()) return;
            int newFps = Integer.parseInt(rb.getText().toString());
            updateConfiguration(ConfigType.FPS, newFps);
            tvFps.setText(String.valueOf(newFps));
            popupWindow.dismiss();
        });

        popupWindow.showAsDropDown(v, 0, 10);
    }

    private void updateQualityRB(RadioButton rb, String label, String current, HardwareInfo hw) {
        boolean supported = false;
        for (HardwareInfo.VideoCapability c : hw.videoCapabilities) { if (c.qualityLabel.equals(label)) { supported = true; break; } }
        rb.setEnabled(supported);
        rb.setAlpha(supported ? 1.0f : 0.4f);
        rb.setChecked(label.equals(current));
    }

    private void updateFpsRB(RadioButton rb, int fps, int current, HardwareInfo.VideoCapability cap) {
        boolean supported = cap.supportedFps.contains(fps);
        rb.setEnabled(supported);
        rb.setAlpha(supported ? 1.0f : 0.4f);
        rb.setChecked(fps == current);
    }

    private void validateFpsForQuality(String quality) {
        HardwareInfo hardware = HardwareInfo.getInstance();
        for (HardwareInfo.VideoCapability c : hardware.videoCapabilities) {
            if (c.qualityLabel.equals(quality)) {
                if (!c.supportedFps.contains(settingsStore.getCurrentFps())) {
                    int fallback = c.supportedFps.isEmpty() ? 30 : c.supportedFps.get(0);
                    updateConfiguration(ConfigType.FPS, fallback);
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
        updateConfiguration(ConfigType.ASPECT_RATIO, ratioLabel);
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
        CenterLayoutManager layoutManager = new CenterLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvModes.setLayoutManager(layoutManager);

        LinearSnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(rvModes);

        rvModes.post(() -> {
            int initialPosition = settingsStore.getCaptureMode() == CaptureMode.VIDEO ? 0 : 1;
            modeAdapter.setSelectedPosition(initialPosition);
            layoutManager.scrollToPosition(initialPosition);
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
        triggerVibration();
        if (modeAdapter != null && rvModes != null) {
            int targetPos = isVideo ? 0 : 1;
            if (modeAdapter.getSelectedPosition() != targetPos) {
                modeAdapter.setSelectedPosition(targetPos);
                rvModes.smoothScrollToPosition(targetPos);
            }
        }

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
        if (rvZoom != null) {
            for (int i = 0; i < rvZoom.getChildCount(); i++) {
                View child = rvZoom.getChildAt(i);
                View tvValue = child.findViewById(R.id.tvZoomValue);
                if (tvValue != null) rotateView(tvValue, (float)degrees);
            }
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
        
        // Reload and apply settings from SharedPreferences
        applySavedSettings();
    }

    private void applySavedSettings() {
        String savedRes = prefs.getString("pref_resolution", "12mp");
        updateConfiguration(ConfigType.RESOLUTION, savedRes);
        if (tvResolution != null) tvResolution.setText(savedRes);

        String savedRatio = prefs.getString("pref_aspect_ratio", "4:3");
        setAspectRatio(savedRatio);

        String savedVQual = prefs.getString("pref_video_quality", "HD");
        int savedFps = prefs.getInt("pref_video_fps", 30);
        cameraManager.updateVideoConfig(savedVQual, savedFps);
        if (tvFps != null) tvFps.setText(String.valueOf(savedFps));
        
        updateModeUi(settingsStore.getCaptureMode() == CaptureMode.VIDEO);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (orientationEventListener != null) orientationEventListener.disable();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void triggerVibration() {
        if (!prefs.getBoolean("pref_vibration", false)) return;
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(50);
            }
        }
    }

    private void showBlackout() {
        View blackout = new View(this);
        blackout.setBackgroundColor(Color.BLACK);
        blackout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ((ViewGroup) findViewById(android.R.id.content)).addView(blackout);
        blackout.animate().alpha(0).setDuration(150).withEndAction(() -> ((ViewGroup) findViewById(android.R.id.content)).removeView(blackout)).start();
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
            String savedLut = prefs.getString("pref_current_lut", null);
            cameraManager.setCurrentLut(savedLut);
            
            String savedRes = prefs.getString("pref_resolution", "12mp");
            updateConfiguration(ConfigType.RESOLUTION, savedRes);
            
            String savedRatio = prefs.getString("pref_aspect_ratio", "4:3");
            setAspectRatio(savedRatio);
            
            String savedVQual = prefs.getString("pref_video_quality", "HD");
            int savedFps = prefs.getInt("pref_video_fps", 30);
            cameraManager.updateVideoConfig(savedVQual, savedFps);

            CaptureMode savedMode = CaptureMode.PHOTO;
            try {
                savedMode = CaptureMode.valueOf(prefs.getString("pref_capture_mode", "PHOTO"));
            } catch (Exception ignored) {}
            setCaptureMode(savedMode);
            updateModeUi(savedMode == CaptureMode.VIDEO);
        } else {
            setCaptureMode(CaptureMode.PHOTO);
            updateModeUi(false);
        }
    }

    private void setCaptureMode(CaptureMode mode) {
        settingsStore.setCaptureMode(mode);
        cameraManager.startCamera(mode);
    }

    private void updateConfiguration(ConfigType type, Object value) {
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
    public void onShutterClicked() {
        if (settingsStore.getCaptureMode() == CaptureMode.PHOTO) {
            showBlackout();
            if (settingsStore.getCameraState() == CameraState.IDLE) {
                cameraManager.takePhoto();
            }
        } else if (settingsStore.getCameraState() == CameraState.RECORDING) {
            cameraManager.stopRecording();
        } else {
            if (settingsStore.getCameraState() == CameraState.IDLE) {
                cameraManager.startRecording();
            }
        }
    }

    @Override
    public void onSwitchCameraClicked() {
        triggerVibration();
        cameraManager.switchCamera();
    }

    @Override
    public void onZoomSelected(float zoomLevel) {
        triggerVibration();
        cameraManager.setZoomRatio(zoomLevel);
    }

    @Override
    public void onModeChanged(boolean isVideoMode) {
        CaptureMode newMode = isVideoMode ? CaptureMode.VIDEO : CaptureMode.PHOTO;
        if (newMode == settingsStore.getCaptureMode()) return;
        
        setCaptureMode(newMode);
        prefs.edit().putString("pref_capture_mode", newMode.name()).apply();
        updateModeUi(isVideoMode);
        
        onStateChanged(CameraState.IDLE);
    }

    @Override
    public void onPresetSelected(int presetIndex) {
        triggerVibration();
        settingsStore.setCurrentPreset(presetIndex);
    }

    @Override
    public void onStateChanged(CameraState state) {
        settingsStore.setCameraState(state);
        runOnUiThread(() -> {
            View btnShutter = findViewById(R.id.btnShutter);
            if (btnShutter == null) return;
            if (settingsStore.getCaptureMode() == CaptureMode.VIDEO) {
                if (state == CameraState.RECORDING) {
                    btnShutter.setBackgroundResource(R.drawable.bg_shutter_recording);
                } else {
                    btnShutter.setBackgroundResource(R.drawable.bg_shutter_video_idle);
                }
            } else {
                btnShutter.setBackgroundResource(R.drawable.bg_shutter);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hardwareExecutor.shutdown();
    }

    private void setupZoomPanel() {
        rvZoom = findViewById(R.id.rvZoom);
        HardwareInfo info = HardwareInfo.getInstance();
        zoomRatios = new ArrayList<>();
        for (Float ratio : info.zoomRatios) {
            if (ratio == 1.3f) continue;
            zoomRatios.add(ratio);
        }
        zoomAdapter = new ZoomAdapter(zoomRatios, (ratio, position) -> {
            onZoomSelected(ratio);
            currentZoomRatio = ratio;
            zoomAdapter.setSelectedPosition(position);
            rvZoom.smoothScrollToPosition(position);
        });
        rvZoom.setAdapter(zoomAdapter);
        CenterLayoutManager layoutManager = new CenterLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvZoom.setLayoutManager(layoutManager);
        LinearSnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(rvZoom);
        rvZoom.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View centerView = snapHelper.findSnapView(layoutManager);
                    if (centerView != null) {
                        int pos = layoutManager.getPosition(centerView);
                        if (pos != RecyclerView.NO_POSITION && pos != zoomAdapter.getSelectedPosition()) {
                            float ratio = zoomRatios.get(pos);
                            onZoomSelected(ratio);
                            currentZoomRatio = ratio;
                            zoomAdapter.setSelectedPosition(pos);
                        }
                    }
                }
            }
        });
        int defaultPos = zoomRatios.indexOf(1.0f);
        if (defaultPos != -1) {
            zoomAdapter.setSelectedPosition(defaultPos);
            rvZoom.scrollToPosition(defaultPos);
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
                cameraManager.setZoomRatio(currentZoomRatio);
                return true;
            }
        });
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 200) {
                    int currentPosition = (modeAdapter != null) ? modeAdapter.getSelectedPosition() : -1;
                    if (diffX > 0 && currentPosition == 1) {
                        if (rvModes != null) rvModes.smoothScrollToPosition(0);
                        return true;
                    } else if (diffX < 0 && currentPosition == 0) {
                        if (rvModes != null) rvModes.smoothScrollToPosition(1);
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
                if (touchFunc.contains("Dò tìm")) handleObjectTracking(x, y);
                else v.performClick();
            }
            return true;
        });
    }

    private void handleObjectTracking(float x, float y) {
        if (trackingOverlay != null) trackingOverlay.showTrackingBox(x, y);
        cameraManager.triggerManualTracking(x, y);
    }

    private void setupLutSelector() {
        rvLuts = findViewById(R.id.rvLuts);
        lutItems = new ArrayList<>();
        lutItems.add(new LutItem("Gốc", null, null));
        ColorMatrix fujiMatrix = new ColorMatrix(); fujiMatrix.setSaturation(1.2f);
        ColorMatrix bwMatrix = new ColorMatrix(); bwMatrix.setSaturation(0f);
        ColorMatrix retroMatrix = new ColorMatrix(); retroMatrix.set(new float[] {1.2f, 0.0f, 0.0f, 0.0f, 20.0f, 0.0f, 1.0f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.8f, 0.0f, -10.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f});
        ColorMatrix cyberpunkMatrix = new ColorMatrix(); cyberpunkMatrix.set(new float[] {1.0f, 0.0f, 0.0f, 0.0f, 30.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.2f, 0.0f, 50.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f});
        ColorMatrix summerMatrix = new ColorMatrix(); summerMatrix.setSaturation(1.3f);
        ColorMatrix satPlusMatrix = new ColorMatrix(); satPlusMatrix.setSaturation(1.5f);
        lutItems.add(new LutItem("Fuji C400", "fuji_c400.PNG", fujiMatrix));
        lutItems.add(new LutItem("Classic Chrome", "classic_chrome.PNG", null));
        lutItems.add(new LutItem("Cyberpunk", "cyberpunk_800t.PNG", cyberpunkMatrix));
        lutItems.add(new LutItem("Retro", "retro_1.PNG", retroMatrix));
        lutItems.add(new LutItem("Monochrome", "leica_monochrome.PNG", bwMatrix));
        lutItems.add(new LutItem("Backrooms", "backrooms.PNG", null));
        lutItems.add(new LutItem("Kodacolor", "kodacolor_100.PNG", null));
        lutItems.add(new LutItem("Superia", "fuji_superia_400.PNG", null));
        lutItems.add(new LutItem("Summer", "summer.PNG", summerMatrix));
        lutItems.add(new LutItem("Light Skin", "light_skin.PNG", null));
        lutItems.add(new LutItem("Saturation+", "saturation+.PNG", satPlusMatrix));
        lutItems.add(new LutItem("Hail Mary", "project_hail_mary.PNG", null));
        lutItems.add(new LutItem("Pro Image 100", "kodak_proimage 100.PNG", null));
        lutAdapter = new LutAdapter(lutItems, (item, position) -> {
            triggerVibration();
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
                Paint paint = new Paint(); paint.setColorFilter(new ColorMatrixColorFilter(item.getColorMatrix()));
                previewView.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
            } else previewView.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    private void updateEvControlStyle(boolean isPanelVisible) {
        if (tvEv == null) return;
        if (isPanelVisible) {
            tvEv.setBackgroundResource(R.drawable.bg_rounded_orange);
            tvEv.setTextColor(Color.WHITE);
        } else {
            tvEv.setBackground(null);
            tvEv.setTextColor(Color.parseColor("#FF9800"));
        }
    }
}
