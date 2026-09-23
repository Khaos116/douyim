package com.zz.douyin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Solid-disc color picker: the angle selects hue, the distance from the
 * center selects saturation (center is white). Value is set externally
 * through {@link #setValue(float)} so the host can offer a brightness slider.
 * Drag or tap anywhere on the disc to pick.
 */
public final class ColorWheelView extends View {
    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    private float hue;
    private float saturation;
    private float value = 1f;
    private float centerX;
    private float centerY;
    private float radius;
    private Bitmap disc;
    private OnColorChangedListener listener;

    public ColorWheelView(Context context) {
        super(context);
        init();
    }

    public ColorWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        markerStrokePaint.setStyle(Paint.Style.STROKE);
        markerStrokePaint.setStrokeWidth(3f * density);
        markerStrokePaint.setColor(0xFFFFFFFF);
        setColor(0xFFFFFFFF);
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.listener = listener;
    }

    public void setColor(int color) {
        float[] hsv = ColorMath.rgbToHsv(
                (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
        invalidate();
    }

    public void setValue(float value) {
        float clamped = ColorMath.clamp01(value);
        if (clamped != this.value) {
            this.value = clamped;
            invalidate();
            emit();
        }
    }

    public float getValue() {
        return value;
    }

    public int getColor() {
        return ColorMath.hsvToColor(hue, saturation, value);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.min(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        centerX = width / 2f;
        centerY = height / 2f;
        radius = Math.min(width, height) / 2f - 2f;
        disc = buildDisc(width, height);
    }

    private Bitmap buildDisc(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(
                Math.max(1, width), Math.max(1, height),
                Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float dx = x - centerX;
                float dy = y - centerY;
                float r = (float) Math.hypot(dx, dy);
                if (r > radius) {
                    continue;
                }
                float pixelHue = (float) Math.toDegrees(Math.atan2(dy, dx));
                if (pixelHue < 0f) {
                    pixelHue += 360f;
                }
                bitmap.setPixel(x, y, ColorMath.hsvToColor(
                        pixelHue, r / radius, 1f));
            }
        }
        return bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (disc != null) {
            canvas.drawBitmap(disc, 0f, 0f, discPaint);
        }
        float density = getResources().getDisplayMetrics().density;
        double angle = Math.toRadians(hue);
        float markerX = centerX + (float) Math.cos(angle) * saturation * radius;
        float markerY = centerY + (float) Math.sin(angle) * saturation * radius;
        float dotRadius = 10f * density;
        markerFillPaint.setColor(ColorMath.hsvToColor(hue, saturation, 1f));
        canvas.drawCircle(markerX, markerY, dotRadius, markerFillPaint);
        canvas.drawCircle(markerX, markerY, dotRadius, markerStrokePaint);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP) {
            performClick();
            return true;
        }
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE) {
            return super.onTouchEvent(event);
        }
        float dx = event.getX() - centerX;
        float dy = event.getY() - centerY;
        float touchRadius = (float) Math.hypot(dx, dy);
        if (touchRadius > radius * 1.15f) {
            return false;
        }
        float touched = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (touched < 0f) {
            touched += 360f;
        }
        float s = ColorMath.clamp01(touchRadius / radius);
        if (touched != hue || s != saturation) {
            hue = touched;
            saturation = s;
            invalidate();
            emit();
        }
        return true;
    }

    private void emit() {
        OnColorChangedListener target = listener;
        if (target != null) {
            target.onColorChanged(getColor());
        }
    }
}
