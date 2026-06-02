package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class ExposureSlider extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();
    
    private final int minIndex = -6; 
    private final int maxIndex = 6;  
    private int currentIndex = 0;
    private final float stepValue = 0.333333f;
    private OnValueChangeListener listener;

    public interface OnValueChangeListener {
        void onValueChanged(int index, float ev);
    }

    public ExposureSlider(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(3f);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(52f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        symbolPaint.setColor(Color.WHITE);
        symbolPaint.setTextSize(48f);
        symbolPaint.setTextAlign(Paint.Align.CENTER);
        indicatorPaint.setColor(Color.parseColor("#F57C00")); 
        indicatorPaint.setStyle(Paint.Style.FILL);
    }

    public void setProgress(int index) {
        this.currentIndex = Math.max(minIndex, Math.min(index, maxIndex));
        invalidate();
    }

    public void setRange(int min, int max, float step) { invalidate(); }

    public void setOnValueChangeListener(OnValueChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth(), height = getHeight();
        float centerY = height / 2f + 10f, padding = 160f, availableWidth = width - 2 * padding;
        float stepWidth = availableWidth / (maxIndex - minIndex);

        for (int i = minIndex; i <= maxIndex; i++) {
            float x = padding + (i - minIndex) * stepWidth;
            float ev = i * stepValue;
            if (Math.abs(ev - Math.round(ev)) < 0.05) {
                int val = Math.abs(Math.round(ev));
                canvas.drawText(String.valueOf(val), x, centerY + 18, textPaint);
                if (i == minIndex) canvas.drawText("-", x - 85, centerY + 18, symbolPaint);
                else if (i == maxIndex) canvas.drawText("+", x + 85, centerY + 18, symbolPaint);
            } else {
                canvas.drawLine(x, centerY - 25, x, centerY + 25, linePaint);
            }
        }
        
        float indicatorX = padding + (currentIndex - minIndex) * stepWidth;
        arrowPath.reset();
        arrowPath.moveTo(indicatorX, centerY - 45);
        arrowPath.lineTo(indicatorX - 22, centerY - 75);
        arrowPath.lineTo(indicatorX + 22, centerY - 75);
        arrowPath.close();
        canvas.drawPath(arrowPath, indicatorPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float padding = 160f, availableWidth = getWidth() - 2 * padding;
            int index = minIndex + Math.round(((event.getX() - padding) / availableWidth) * (maxIndex - minIndex));
            index = Math.max(minIndex, Math.min(index, maxIndex));
            if (index != currentIndex) {
                currentIndex = index;
                invalidate();
                if (listener != null) listener.onValueChanged(currentIndex, currentIndex * stepValue);
            }
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() { return super.performClick(); }
}
