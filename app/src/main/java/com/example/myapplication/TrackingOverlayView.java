package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class TrackingOverlayView extends View {
    private final Paint paint;
    private RectF trackingRect;
    private boolean isVisible = false;
    private final Runnable hideRunnable = () -> {
        isVisible = false;
        invalidate();
    };

    public TrackingOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.parseColor("#03ce1b")); // Green
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
    }

    public void showTrackingBox(float x, float y) {
        float size = 100; // Default touch box size
        trackingRect = new RectF(x - size, y - size, x + size, y + size);
        isVisible = true;
        invalidate();
        
        resetTimer(3000);
    }

    public void updateBoundingBox(android.graphics.Rect rect, boolean isEye) {
        if (isEye) {
            // Force 20x20 centered on the provided rect center
            float cx = rect.centerX();
            float cy = rect.centerY();
            trackingRect = new RectF(cx - 10, cy - 10, cx + 10, cy + 10);
        } else {
            trackingRect = new RectF(rect);
        }
        isVisible = true;
        invalidate();
        resetTimer(1000);
    }

    public void clear() {
        isVisible = false;
        invalidate();
    }

    private void resetTimer(long ms) {
        removeCallbacks(hideRunnable);
        postDelayed(hideRunnable, ms);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isVisible && trackingRect != null) {
            canvas.drawRect(trackingRect, paint);
        }
    }
}
