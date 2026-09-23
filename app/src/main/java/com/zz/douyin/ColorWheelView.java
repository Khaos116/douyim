package com.zz.douyin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Circular color picker: the outer ring selects hue, the inner square selects
 * saturation (left-right) and value (top-bottom). Drag or tap anywhere to
 * pick; the opaque picked color is reported through the listener.
 */
public final class ColorWheelView extends View {
    private static final int[] HUE_RING_COLORS = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
            0xFF0000FF, 0xFFFF00FF, 0xFFFF0000,
    };

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint saturationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float hue;
    private float saturation = 1f;
    private float value = 1f;
    private float centerX;
    private float centerY;
    private float outerRadius;
    private float ringThickness;
    private float squareHalf;
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
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(22f * density);
        markerFillPaint.setStyle(Paint.Style.FILL);
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
        updateSquarePaints();
        invalidate();
    }

    public int getColor() {
        return ColorMath.hsvToColor(hue, saturation, value);
    }

    public float getHue() {
        return hue;
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
        float size = Math.min(width, height);
        centerX = width / 2f;
        centerY = height / 2f;
        outerRadius = size / 2f - ringPaint.getStrokeWidth() / 2f - 2f;
        float innerRadius = outerRadius - ringPaint.getStrokeWidth();
        ringThickness = ringPaint.getStrokeWidth();
        squareHalf = innerRadius * 0.68f;
        ringPaint.setShader(new SweepGradient(centerX, centerY, HUE_RING_COLORS, null));
        updateSquarePaints();
    }

    private void updateSquarePaints() {
        int hueColor = ColorMath.hsvToColor(hue, 1f, 1f);
        float left = centerX - squareHalf;
        float right = centerX + squareHalf;
        float top = centerY - squareHalf;
        float bottom = centerY + squareHalf;
        saturationPaint.setShader(new LinearGradient(
                left, top, right, top,
                0xFFFFFFFF, hueColor, Shader.TileMode.CLAMP));
        valuePaint.setShader(new LinearGradient(
                left, top, left, bottom,
                0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float ringRadius = outerRadius - ringThickness / 2f;
        canvas.drawCircle(centerX, centerY, ringRadius, ringPaint);
        float left = centerX - squareHalf;
        float top = centerY - squareHalf;
        float side = squareHalf * 2f;
        canvas.drawRect(left, top, left + side, top + side, saturationPaint);
        canvas.drawRect(left, top, left + side, top + side, valuePaint);
        drawMarkers(canvas, ringRadius, left, top, side);
    }

    private void drawMarkers(
            Canvas canvas, float ringRadius, float left, float top, float side) {
        float density = getResources().getDisplayMetrics().density;
        double angle = Math.toRadians(hue);
        float hueX = centerX + (float) Math.cos(angle) * ringRadius;
        float hueY = centerY + (float) Math.sin(angle) * ringRadius;
        float markerRadius = ringThickness * 0.72f;
        markerFillPaint.setColor(ColorMath.hsvToColor(hue, 1f, 1f));
        canvas.drawCircle(hueX, hueY, markerRadius, markerFillPaint);
        canvas.drawCircle(hueX, hueY, markerRadius, markerStrokePaint);
        float svX = left + saturation * side;
        float svY = top + (1f - value) * side;
        float dotRadius = 9f * density;
        markerFillPaint.setColor(getColor());
        canvas.drawCircle(svX, svY, dotRadius, markerFillPaint);
        canvas.drawCircle(svX, svY, dotRadius, markerStrokePaint);
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
        float ringMid = outerRadius - ringThickness / 2f;
        if (touchRadius >= ringMid - ringThickness && touchRadius <= outerRadius + ringThickness) {
            float touched = (float) Math.toDegrees(Math.atan2(dy, dx));
            if (touched < 0f) {
                touched += 360f;
            }
            if (touched != hue) {
                hue = touched;
                updateSquarePaints();
                emit();
            }
        } else if (touchRadius < ringMid - ringThickness) {
            float side = squareHalf * 2f;
            float s = ColorMath.clamp01((dx + squareHalf) / side);
            float v = ColorMath.clamp01(1f - (dy + squareHalf) / side);
            if (s != saturation || v != value) {
                saturation = s;
                value = v;
                emit();
            }
        } else {
            return false;
        }
        invalidate();
        return true;
    }

    private void emit() {
        OnColorChangedListener target = listener;
        if (target != null) {
            target.onColorChanged(getColor());
        }
    }
}
