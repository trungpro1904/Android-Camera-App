package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;

/**
 * Processor xử lý áp dụng Hald LUT 8 (512x512) lên ảnh.
 * Đã sửa lỗi mapping để khớp chính xác với file Identity Hald chuẩn (Continuous Layout).
 */
public class HaldLutProcessor {

    /**
     * Load file LUT từ assets và đảm bảo không bị Android tự động scale.
     */
    public static Bitmap loadLutFromAssets(Context context, String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        try {
            InputStream is = context.getAssets().open(fileName);
            BitmapFactory.Options options = new BitmapFactory.Options();
            
            // QUAN TRỌNG: Vô hiệu hóa scaling để giữ nguyên kích thước 512x512 của LUT.
            options.inScaled = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Áp dụng thuật toán Hald8 với nội suy tam tuyến.
     */
    public static Bitmap applyHaldLut8(Bitmap srcBitmap, Bitmap lutBitmap) {
        if (lutBitmap == null || srcBitmap == null) return srcBitmap;

        int width = srcBitmap.getWidth();
        int height = srcBitmap.getHeight();
        
        int[] srcPixels = new int[width * height];
        srcBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height);

        int lutW = lutBitmap.getWidth();
        int lutH = lutBitmap.getHeight();
        int[] lutPixels = new int[lutW * lutH];
        lutBitmap.getPixels(lutPixels, 0, lutW, 0, 0, lutW, lutH);

        // Kích thước Hald8 là 64x64x64
        float maxIdx = 63.0f;

        for (int i = 0; i < srcPixels.length; i++) {
            int p = srcPixels[i];
            int a = (p >> 24) & 0xff;
            int r = (p >> 16) & 0xff;
            int g = (p >> 8) & 0xff;
            int b = p & 0xff;

            // Chuyển giá trị 0-255 sang tọa độ 0.0 - 63.0
            float rf = (r / 255.0f) * maxIdx;
            float gf = (g / 255.0f) * maxIdx;
            float bf = (b / 255.0f) * maxIdx;

            // Xác định các đỉnh của khối màu để nội suy
            int r0 = (int) rf;
            int r1 = Math.min(r0 + 1, 63);
            int g0 = (int) gf;
            int g1 = Math.min(g0 + 1, 63);
            int b0 = (int) bf;
            int b1 = Math.min(b0 + 1, 63);

            float dr = rf - r0;
            float dg = gf - g0;
            float db = bf - b0;

            // Lấy màu tại 8 đỉnh xung quanh
            int c000 = getLutPixel(lutPixels, r0, g0, b0);
            int c100 = getLutPixel(lutPixels, r1, g0, b0);
            int c010 = getLutPixel(lutPixels, r0, g1, b0);
            int c110 = getLutPixel(lutPixels, r1, g1, b0);
            int c001 = getLutPixel(lutPixels, r0, g0, b1);
            int c101 = getLutPixel(lutPixels, r1, g0, b1);
            int c011 = getLutPixel(lutPixels, r0, g1, b1);
            int c111 = getLutPixel(lutPixels, r1, g1, b1);

            // Nội suy tam tuyến (Trilinear)
            int outR = (int) interpolate(c000, c100, c010, c110, c001, c101, c011, c111, dr, dg, db, 16);
            int outG = (int) interpolate(c000, c100, c010, c110, c001, c101, c011, c111, dr, dg, db, 8);
            int outB = (int) interpolate(c000, c100, c010, c110, c001, c101, c011, c111, dr, dg, db, 0);

            srcPixels[i] = (a << 24) | (outR << 16) | (outG << 8) | outB;
        }

        Bitmap outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        outBitmap.setPixels(srcPixels, 0, width, 0, 0, width, height);
        return outBitmap;
    }

    private static int getLutPixel(int[] lut, int r, int g, int b) {
        // GIẢI MÃ CHUẨN CHO ẢNH BẠN GỬI:
        // Cấu trúc ảnh 512x512 của bạn là Continuous Layout.
        // Index = R + (G * 64) + (B * 4096)
        // Với chiều rộng 512, pixel tại index này nằm chính xác tại vị trí tương ứng trong mảng lutPixels.
        return lut[r + g * 64 + b * 4096];
    }

    private static float interpolate(int c000, int c100, int c010, int c110, int c001, int c101, int c011, int c111, 
                                     float dr, float dg, float db, int shift) {
        float v000 = (c000 >> shift) & 0xff;
        float v100 = (c100 >> shift) & 0xff;
        float v010 = (c010 >> shift) & 0xff;
        float v110 = (c110 >> shift) & 0xff;
        float v001 = (c001 >> shift) & 0xff;
        float v101 = (c101 >> shift) & 0xff;
        float v011 = (c011 >> shift) & 0xff;
        float v111 = (c111 >> shift) & 0xff;

        return (v000 * (1 - dr) * (1 - dg) * (1 - db) +
                v100 * dr * (1 - dg) * (1 - db) +
                v010 * (1 - dr) * dg * (1 - db) +
                v110 * dr * dg * (1 - db) +
                v001 * (1 - dr) * (1 - dg) * db +
                v101 * dr * (1 - dg) * db +
                v011 * (1 - dr) * dg * db +
                v111 * dr * dg * db);
    }
}
