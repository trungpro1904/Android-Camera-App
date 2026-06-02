package com.example.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import java.util.Locale;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.view.Gravity;

import androidx.appcompat.view.ContextThemeWrapper;

public class SettingActivity extends AppCompatActivity {

    private Switch switchLocation;
    private Switch switchGrid;
    private Switch switchVibration;
    private Switch switchRuler;
    private Switch switchHistogram;

    private TextView tvDefaultPhotoFormat;
    private TextView tvDefaultResolution;
    private TextView tvRawComp;
    private TextView tvDefaultVideoFormat;
    private TextView tvDefaultAspectRatio;
    private TextView tvStartMode;
    private TextView tvVolButton;
    private TextView tvTouchScreen;

    private SharedPreferences prefs;

    private final ActivityResultLauncher<String> requestLocationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    prefs.edit().putBoolean("pref_location", true).apply();
                } else {
                    switchLocation.setChecked(false);
                    prefs.edit().putBoolean("pref_location", false).apply();
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        prefs = getSharedPreferences("camera_prefs", MODE_PRIVATE);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Switches
        switchLocation = findViewById(R.id.switchLocation);
        switchGrid = findViewById(R.id.switchGrid);
        switchVibration = findViewById(R.id.switchVibration);
        switchRuler = findViewById(R.id.switchRuler);
        switchHistogram = findViewById(R.id.switchHistogram);

        // Detail TextViews
        tvDefaultPhotoFormat = findViewById(R.id.tvDefaultPhotoFormat);
        tvDefaultResolution = findViewById(R.id.tvDefaultResolution);
        tvRawComp = findViewById(R.id.tvRawComp);
        tvDefaultVideoFormat = findViewById(R.id.tvDefaultVideoFormat);
        tvDefaultAspectRatio = findViewById(R.id.tvDefaultAspectRatio);
        tvStartMode = findViewById(R.id.tvStartMode);
        tvVolButton = findViewById(R.id.tvVolButton);
        tvTouchScreen = findViewById(R.id.tvTouchScreen);

        loadSettings();
        setupListeners();
    }

    private void loadSettings() {
        switchLocation.setChecked(prefs.getBoolean("pref_location", false));
        switchGrid.setChecked(prefs.getBoolean("pref_grid", false));
        switchVibration.setChecked(prefs.getBoolean("pref_vibration", false));
        
        // Ruler and Histogram disabled as requested
        switchRuler.setChecked(false);
        switchRuler.setEnabled(false);
        switchRuler.setAlpha(0.3f);
        switchHistogram.setChecked(false);
        switchHistogram.setEnabled(false);
        switchHistogram.setAlpha(0.3f);

        tvDefaultPhotoFormat.setText(prefs.getString("pref_photo_format", "JPEG"));
        tvDefaultResolution.setText(prefs.getString("pref_resolution", "12mp"));
        tvRawComp.setText(prefs.getString("pref_raw_comp", "Không nén"));
        
        String vQual = prefs.getString("pref_video_quality", "HD");
        int vFps = prefs.getInt("pref_video_fps", 60);
        String videoFormat = String.format(Locale.getDefault(), "%s %dfps", vQual, vFps);
        tvDefaultVideoFormat.setText(videoFormat);
        
        tvDefaultAspectRatio.setText(prefs.getString("pref_aspect_ratio", "4:3"));
        tvStartMode.setText(prefs.getString("pref_start_mode", "CĐ chụp trước"));
        tvVolButton.setText(prefs.getString("pref_vol_func", "Zoom"));
        tvTouchScreen.setText(prefs.getString("pref_touch_func", "Dò tìm\nđối tượng"));
    }

    private void setupListeners() {
        switchLocation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                } else {
                    prefs.edit().putBoolean("pref_location", true).apply();
                }
            } else {
                prefs.edit().putBoolean("pref_location", false).apply();
            }
            triggerVibrationIfNeeded();
        });

        switchGrid.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("pref_grid", isChecked).apply();
            triggerVibrationIfNeeded();
        });

        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("pref_vibration", isChecked).apply();
            if (isChecked) triggerVibration();
        });

        // Detail click listeners
        tvDefaultPhotoFormat.setOnClickListener(v -> showPhotoFormatPopup());
        tvDefaultResolution.setOnClickListener(v -> showResolutionPopup());
        tvRawComp.setOnClickListener(v -> showRawCompPopup());
        tvDefaultVideoFormat.setOnClickListener(v -> showVideoFormatPopup());
        tvDefaultAspectRatio.setOnClickListener(v -> showAspectRatioPopup());
        tvStartMode.setOnClickListener(v -> showStartModePopup());
        tvVolButton.setOnClickListener(v -> showVolFuncPopup());
        tvTouchScreen.setOnClickListener(v -> showTouchFuncPopup());
    }

    private void showPhotoFormatPopup() {
        String[] options = {"JPEG", "RAW", "RAW+JPEG"};
        String desc = "Trong chế độ Basic chỉ cho phép định dạng JPEG.";
        showDetailPopup("Định dạng ảnh mặc định", options, desc, "pref_photo_format", (val) -> {
            tvDefaultPhotoFormat.setText(val);
            // Disable RAW options if in basic mode logic would go here if needed
        }, "JPEG"); // JPEG is default and others might be disabled if needed
    }

    private void showResolutionPopup() {
        String[] options = {"48mp", "24mp", "12mp"};
        String desc = "Kích thước file ảnh tỷ lệ thuận với độ phân giải, đồng thời độ phân giải càng cao, ảnh càng chi tiết.";
        showDetailPopup("Độ phân giải mặc định", options, desc, "pref_resolution", (val) -> tvDefaultResolution.setText(val), null);
    }

    private void showRawCompPopup() {
        String[] options = {"Không nén", "Nén không mất mát", "Nén"};
        String desc = "Ảnh RAW chứa lượng thông tin rất lớn mà cảm biến thu nhận được, chọn nén hoặc không phù hợp sẽ giảm tải bộ nhớ.";
        showDetailPopup("Nén ảnh RAW", options, desc, "pref_raw_comp", (val) -> tvRawComp.setText(val), null);
    }

    private void showVideoFormatPopup() {
        // This is a bit more complex in the image (Quality + FPS)
        // For simplicity using showDetailPopup with custom logic if needed or separate popup
        View customView = LayoutInflater.from(this).inflate(R.layout.layout_setting_detail, null);
        PopupWindow popupWindow = new PopupWindow(customView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
        
        ((TextView)customView.findViewById(R.id.tvDetailTitle)).setText("Định dạng video mặc định");
        ((TextView)customView.findViewById(R.id.tvDetailDesc)).setText("Độ phân giải video và tốc độ khung hình tỷ lệ thuận với kích thước file video.\n*Lưu ý rằng việc quay video với độ phân giải lớn và FPS cao có thể làm nóng máy.");
        
        RadioGroup rg = customView.findViewById(R.id.rgOptions);
        String[] quals = {"4K", "HD", "720p"};
        String currentQual = prefs.getString("pref_video_quality", "HD");
        for (String q : quals) {
            RadioButton rb = new RadioButton(new ContextThemeWrapper(this, R.style.SettingRadioButton), null, 0);
            rb.setText(q);
            rb.setId(View.generateViewId());
            rg.addView(rb);
            if (q.equals(currentQual)) rb.setChecked(true);
            rb.setOnClickListener(v -> prefs.edit().putString("pref_video_quality", q).apply());
        }

        TextView tvFpsHeader = new TextView(this);
        tvFpsHeader.setText("Tốc độ khung hình(FPS)");
        tvFpsHeader.setTextColor(Color.WHITE);
        tvFpsHeader.setPadding(0, 20, 0, 10);
        rg.addView(tvFpsHeader);

        int[] fpsOptions = {120, 60, 30};
        int currentFps = prefs.getInt("pref_video_fps", 60);
        for (int f : fpsOptions) {
            RadioButton rb = new RadioButton(new ContextThemeWrapper(this, R.style.SettingRadioButton), null, 0);
            rb.setText(String.valueOf(f));
            rg.addView(rb);
            if (f == currentFps) rb.setChecked(true);
            rb.setOnClickListener(v -> prefs.edit().putInt("pref_video_fps", f).apply());
        }

        customView.findViewById(R.id.btnDetailClose).setOnClickListener(v -> {
            loadSettings();
            popupWindow.dismiss();
        });
        popupWindow.showAtLocation(tvDefaultVideoFormat, Gravity.CENTER, 0, 0);
    }

    private void showAspectRatioPopup() {
        String[] options = {"4:3", "16:9", "1:1"};
        showDetailPopup("Tỷ lệ khung hình mặc định", options, "", "pref_aspect_ratio", (val) -> tvDefaultAspectRatio.setText(val), null);
    }

    private void showStartModePopup() {
        String[] options = {"CĐ chụp trước", "Luôn dùng chế độ Basic"};
        showDetailPopup("Chế độ chụp khởi chạy", options, "", "pref_start_mode", (val) -> tvStartMode.setText(val), null);
    }

    private void showVolFuncPopup() {
        String[] options = {"Zoom", "Chụp"};
        showDetailPopup("Dùng phím âm lượng là", options, "", "pref_vol_func", (val) -> tvVolButton.setText(val), null);
    }

    private void showTouchFuncPopup() {
        String[] options = {"Dò tìm đối tượng", "Tiêu điểm và độ sáng"};
        String desc = "Dò tìm đối tượng thích hợp nhất khi muốn dõi theo chủ thể chuyển động.\n\nTiêu điểm và độ sáng cho phép máy tính toán lượng ánh sáng phù hợp cho vùng được chọn khi có 1 trong 2 thông số S hoặc ISO là AUTO.\n\nCả 2 chế độ đều cho phép tự động lấy nét";
        showDetailPopup("Chạm vào màn hình để", options, desc, "pref_touch_func", (val) -> tvTouchScreen.setText(val.replace(" ", "\n")), null);
    }

    private interface OnValueSelected { void onSelected(String val); }

    private void showDetailPopup(String title, String[] options, String desc, String prefKey, OnValueSelected callback, String enabledOnly) {
        View customView = LayoutInflater.from(this).inflate(R.layout.layout_setting_detail, null);
        PopupWindow popupWindow = new PopupWindow(customView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);

        ((TextView)customView.findViewById(R.id.tvDetailTitle)).setText(title);
        ((TextView)customView.findViewById(R.id.tvDetailDesc)).setText(desc);

        RadioGroup rg = customView.findViewById(R.id.rgOptions);
        String current = prefs.getString(prefKey, options[0]);

        for (String opt : options) {
            RadioButton rb = new RadioButton(new ContextThemeWrapper(this, R.style.SettingRadioButton), null, 0);
            rb.setText(opt);
            rg.addView(rb);
            if (opt.equals(current)) rb.setChecked(true);
            
            // Disable logic
            if (enabledOnly != null && !opt.equals(enabledOnly)) {
                rb.setEnabled(false);
                rb.setAlpha(0.3f);
            }

            rb.setOnClickListener(v -> {
                prefs.edit().putString(prefKey, opt).apply();
                callback.onSelected(opt);
                popupWindow.dismiss();
            });
        }

        customView.findViewById(R.id.btnDetailClose).setOnClickListener(v -> popupWindow.dismiss());
        popupWindow.showAtLocation(tvDefaultPhotoFormat, Gravity.CENTER, 0, 0);
    }

    private void triggerVibrationIfNeeded() {
        if (prefs.getBoolean("pref_vibration", false)) {
            triggerVibration();
        }
    }

    private void triggerVibration() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(50);
            }
        }
    }
}
