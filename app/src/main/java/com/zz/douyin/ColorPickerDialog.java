package com.zz.douyin;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Modal color picker: hue wheel plus an opacity slider, live preview and hex
 * readout. Reports the picked ARGB color when the user confirms.
 */
public final class ColorPickerDialog {
    private static final int BACKGROUND = Color.rgb(28, 29, 34);
    private static final int TEXT_PRIMARY = Color.rgb(245, 245, 248);
    private static final int TEXT_SECONDARY = Color.rgb(169, 171, 180);
    private static final int ACCENT = Color.rgb(254, 44, 85);
    private static final int FIELD = Color.rgb(38, 39, 45);
    private static final int FIELD_EDGE = Color.rgb(58, 60, 68);

    public interface OnColorPickedListener {
        void onColorPicked(int color);
    }

    private ColorPickerDialog() {
    }

    public static void show(
            Activity activity,
            String title,
            int initialColor,
            OnColorPickedListener listener
    ) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20f * density);
        root.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(BACKGROUND);
        background.setCornerRadius(16f * density);
        root.setBackground(background);

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(18);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setTextColor(TEXT_PRIMARY);
        root.addView(titleView, matchWrap());

        ColorWheelView wheel = new ColorWheelView(activity);
        int wheelSize = Math.round(260f * density);
        LinearLayout.LayoutParams wheelParams =
                new LinearLayout.LayoutParams(wheelSize, wheelSize);
        wheelParams.gravity = Gravity.CENTER_HORIZONTAL;
        wheelParams.topMargin = Math.round(12f * density);
        root.addView(wheel, wheelParams);

        TextView alphaLabel = new TextView(activity);
        alphaLabel.setText("不透明度");
        alphaLabel.setTextSize(14);
        alphaLabel.setTextColor(TEXT_PRIMARY);
        LinearLayout.LayoutParams alphaLabelParams = matchWrap();
        alphaLabelParams.topMargin = Math.round(14f * density);
        root.addView(alphaLabel, alphaLabelParams);

        SeekBar alphaBar = new SeekBar(activity);
        alphaBar.setMax(255);
        LinearLayout.LayoutParams alphaParams = matchWrap();
        alphaParams.topMargin = Math.round(4f * density);
        root.addView(alphaBar, alphaParams);

        LinearLayout previewRow = new LinearLayout(activity);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams previewParams = matchWrap();
        previewParams.topMargin = Math.round(14f * density);
        root.addView(previewRow, previewParams);

        View swatch = new View(activity);
        int swatchSize = Math.round(44f * density);
        previewRow.addView(
                swatch, new LinearLayout.LayoutParams(swatchSize, swatchSize));

        TextView hexView = new TextView(activity);
        hexView.setTextSize(16);
        hexView.setTextColor(TEXT_SECONDARY);
        LinearLayout.LayoutParams hexParams = wrapWrap();
        hexParams.leftMargin = Math.round(12f * density);
        previewRow.addView(hexView, hexParams);

        LinearLayout buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = Math.round(18f * density);
        root.addView(buttonRow, buttonParams);

        Button cancel = new Button(activity);
        cancel.setText("取消");
        cancel.setTextSize(15);
        cancel.setAllCaps(false);
        cancel.setTextColor(TEXT_PRIMARY);
        cancel.setBackground(buttonBackground(FIELD, density));
        buttonRow.addView(cancel, wrapWrap());

        Button confirm = new Button(activity);
        confirm.setText("确定");
        confirm.setTextSize(15);
        confirm.setAllCaps(false);
        confirm.setTextColor(Color.WHITE);
        confirm.setBackground(buttonBackground(ACCENT, density));
        LinearLayout.LayoutParams confirmParams = wrapWrap();
        confirmParams.leftMargin = Math.round(12f * density);
        buttonRow.addView(confirm, confirmParams);

        int[] opaque = {initialColor | 0xFF000000};
        wheel.setColor(opaque[0]);
        alphaBar.setProgress((initialColor >>> 24) & 0xFF);
        Runnable refresh = () -> {
            int picked = ((alphaBar.getProgress() & 0xFF) << 24) | (opaque[0] & 0xFFFFFF);
            GradientDrawable swatchBg = new GradientDrawable();
            swatchBg.setColor(picked);
            swatchBg.setCornerRadius(10f * density);
            swatchBg.setStroke(Math.round(1f * density), FIELD_EDGE);
            swatch.setBackground(swatchBg);
            hexView.setText(ColorMath.toHex(picked));
        };
        wheel.setOnColorChangedListener(color -> {
            opaque[0] = color;
            refresh.run();
        });
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                refresh.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        cancel.setOnClickListener(ignored -> dialog.dismiss());
        confirm.setOnClickListener(ignored -> {
            int picked = ((alphaBar.getProgress() & 0xFF) << 24) | (opaque[0] & 0xFFFFFF);
            dialog.dismiss();
            if (listener != null) {
                listener.onColorPicked(picked);
            }
        });
        refresh.run();

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private static GradientDrawable buttonBackground(int color, float density) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(12f * density);
        return drawable;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
