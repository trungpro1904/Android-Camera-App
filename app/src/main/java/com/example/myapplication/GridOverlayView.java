package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class GridOverlayView extends View {
    private Paint paint;

    public GridOverlayView(Context context) {
        super(context);
        init();
    }

    public GridOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GridOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2f);
        paint.setAlpha(128); // 50% transparency
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        float oneThirdWidth = width / 3f;
        float twoThirdsWidth = 2 * width / 3f;

        float oneThirdHeight = height / 3f;
        float twoThirdsHeight = 2 * height / 3f;

        // Draw vertical lines
        canvas.drawLine(oneThirdWidth, 0, oneThirdWidth, height, paint);
        canvas.drawLine(twoThirdsWidth, 0, twoThirdsWidth, height, paint);

        // Draw horizontal lines
        canvas.drawLine(0, oneThirdHeight, width, oneThirdHeight, paint);
        canvas.drawLine(0, twoThirdsHeight, width, twoThirdsHeight, paint);
    }
}
