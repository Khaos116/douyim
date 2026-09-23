package com.zz.douyin;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.zz.douyin.hook.LogBook;

import java.io.File;
import java.util.List;

/**
 * In-app log viewer: reads the day-rotated files {@link LogBook} writes from
 * the Douyin process, so issues can be diagnosed and shared without adb.
 */
public final class LogViewerActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(16, 17, 20);
    private static final int CARD = Color.rgb(28, 29, 34);
    private static final int PRIMARY = Color.rgb(254, 44, 85);
    private static final int TEXT_PRIMARY = Color.rgb(245, 245, 248);
    private static final int TEXT_SECONDARY = Color.rgb(169, 171, 180);
    private static final int REQUEST_STORAGE = 1001;

    private TextView statusView;
    private LinearLayout fileContainer;
    private TextView logView;
    private ScrollView logScroll;
    private Button permissionButton;
    private Button shareButton;
    private final Button[] levelButtons = new Button[4];
    private File selectedFile;
    private char minLevel = 'D';
    private String currentText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // onResume also runs after onCreate, and again when returning from
        // the all-files-access settings page, so one reload covers both.
        reload();
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(20), dp(32), dp(20), dp(32));

        TextView title = text("运行日志", 28, TEXT_PRIMARY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView subtitle = text("抖音进程内写入，可直接查看与分享", 15, PRIMARY);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(4);
        root.addView(subtitle, subtitleParams);

        statusView = text("", 13, TEXT_SECONDARY);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(12);
        root.addView(statusView, statusParams);

        TextView fileHeader = sectionTitle("日志文件");
        LinearLayout.LayoutParams fileHeaderParams = matchWrap();
        fileHeaderParams.topMargin = dp(12);
        root.addView(fileHeader, fileHeaderParams);
        ScrollView fileScroll = new ScrollView(this);
        LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        fileParams.topMargin = dp(8);
        root.addView(fileScroll, fileParams);
        fileContainer = new LinearLayout(this);
        fileContainer.setOrientation(LinearLayout.VERTICAL);
        fileScroll.addView(fileContainer, matchWrap());

        TextView levelHeader = sectionTitle("级别过滤");
        LinearLayout.LayoutParams levelHeaderParams = matchWrap();
        levelHeaderParams.topMargin = dp(16);
        root.addView(levelHeader, levelHeaderParams);
        LinearLayout levelRow = new LinearLayout(this);
        levelRow.setOrientation(LinearLayout.HORIZONTAL);
        levelRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams levelRowParams = matchWrap();
        levelRowParams.topMargin = dp(8);
        root.addView(levelRow, levelRowParams);
        String[] labels = {"全部", "信息+", "警告+", "错误"};
        char[] levels = {'D', 'I', 'W', 'E'};
        for (int index = 0; index < labels.length; index++) {
            final char level = levels[index];
            Button button = smallButton(labels[index]);
            button.setOnClickListener(view -> {
                minLevel = level;
                refreshLevelButtons();
                loadContent();
            });
            levelButtons[index] = button;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (index > 0) {
                params.leftMargin = dp(8);
            }
            levelRow.addView(button, params);
        }
        refreshLevelButtons();

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = matchWrap();
        actionParams.topMargin = dp(12);
        root.addView(actionRow, actionParams);
        Button refreshButton = smallButton("刷新");
        refreshButton.setOnClickListener(view -> reload());
        actionRow.addView(refreshButton, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        shareButton = smallButton("分享");
        shareButton.setOnClickListener(view -> shareCurrent());
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        shareParams.leftMargin = dp(8);
        actionRow.addView(shareButton, shareParams);
        Button clearButton = smallButton("清空");
        clearButton.setOnClickListener(view -> {
            if (LogBook.clearLogs()) {
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "部分日志删除失败", Toast.LENGTH_SHORT).show();
            }
            reload();
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clearParams.leftMargin = dp(8);
        actionRow.addView(clearButton, clearParams);

        permissionButton = new Button(this);
        permissionButton.setText("授予存储权限以读取日志");
        permissionButton.setTextColor(Color.WHITE);
        permissionButton.setTextSize(15);
        permissionButton.setAllCaps(false);
        permissionButton.setBackground(rounded(PRIMARY, 12));
        permissionButton.setOnClickListener(view -> requestStoragePermission());
        LinearLayout.LayoutParams permissionParams = matchWrap();
        permissionParams.topMargin = dp(12);
        root.addView(permissionButton, permissionParams);

        logScroll = new ScrollView(this);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f);
        logParams.topMargin = dp(12);
        root.addView(logScroll, logParams);
        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(12);
        logView.setTextColor(TEXT_PRIMARY);
        logView.setTextIsSelectable(true);
        logView.setBackground(rounded(CARD, 12));
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));
        logScroll.addView(logView, matchWrap());
        return root;
    }

    private void reload() {
        List<File> files;
        try {
            files = LogBook.listLogFiles();
        } catch (RuntimeException failed) {
            files = null;
        }
        fileContainer.removeAllViews();
        if (files == null || files.isEmpty()) {
            selectedFile = null;
            currentText = "";
            logView.setText("");
            diagnoseEmpty();
            return;
        }
        permissionButton.setVisibility(View.GONE);
        if (selectedFile == null || !files.contains(selectedFile)) {
            selectedFile = files.get(0);
        }
        statusView.setText("共 " + files.size() + " 个日志文件（只保留最近 7 天）");
        statusView.setTextColor(TEXT_SECONDARY);
        for (File file : files) {
            Button button = fileButton(
                    file.getName() + " · " + formatSize(file.length()),
                    file.equals(selectedFile)
            );
            final File target = file;
            button.setOnClickListener(view -> {
                selectedFile = target;
                reload();
            });
            LinearLayout.LayoutParams params = matchWrap();
            params.topMargin = dp(6);
            fileContainer.addView(button, params);
        }
        loadContent();
    }

    private void diagnoseEmpty() {
        if (needsFilePermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                statusView.setText("需要“允许管理所有文件”才能读取日志；"
                        + "点下方按钮去设置中打开，回退后自动刷新");
            } else {
                statusView.setText("需要存储权限才能读取日志；点下方按钮授权");
            }
            statusView.setTextColor(Color.rgb(255, 166, 77));
            permissionButton.setVisibility(View.VISIBLE);
            return;
        }
        File dir = LogBook.logDir();
        boolean exists = false;
        boolean readable = false;
        try {
            exists = dir.exists();
            readable = dir.canRead() && dir.listFiles() != null;
        } catch (RuntimeException ignored) {
            // Diagnose conservatively below.
        }
        if (!exists) {
            statusView.setText("暂无日志：抖音进程尚未写入（打开一次抖音并播放后重试）");
            statusView.setTextColor(Color.rgb(255, 166, 77));
            permissionButton.setVisibility(View.GONE);
            return;
        }
        if (!readable) {
            // Permission is already granted here; something else blocks reads.
            statusView.setText("已有权限但目录仍不可读，请用 adb 导出：\n"
                    + "adb logcat -v threadtime -s DouyinImmersive");
            statusView.setTextColor(Color.rgb(255, 166, 77));
            permissionButton.setVisibility(View.GONE);
            return;
        }
        statusView.setText("目录可读但没有日志文件");
        statusView.setTextColor(TEXT_SECONDARY);
        permissionButton.setVisibility(View.GONE);
    }

    private void loadContent() {
        if (selectedFile == null) {
            currentText = "";
            logView.setText("");
            return;
        }
        String text;
        try {
            text = LogBook.readTail(selectedFile, 4000, minLevel);
        } catch (RuntimeException failed) {
            text = "";
        }
        currentText = text;
        logView.setText(text.isEmpty() ? "(空)" : text);
        logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void shareCurrent() {
        if (currentText == null || currentText.isEmpty()) {
            Toast.makeText(this, "当前没有可分享的日志", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "抖仙人运行日志");
        share.putExtra(Intent.EXTRA_TEXT, currentText);
        startActivity(Intent.createChooser(share, "分享日志"));
    }

    /**
     * On Android 11+ another app's media dir is invisible without all-files
     * access ({@code exists()} lies and returns false), so the permission
     * state must be checked before trusting any {@link java.io.File} probe.
     */
    @TargetApi(Build.VERSION_CODES.R)
    private boolean needsFilePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return !Environment.isExternalStorageManager();
            } catch (RuntimeException ignored) {
                return true;
            }
        }
        try {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestAllFilesAccess();
            return;
        }
        try {
            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    },
                    REQUEST_STORAGE
            );
        } catch (RuntimeException failed) {
            Toast.makeText(this, "无法请求权限，请用 adb 导出日志", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Android 11+ gates another app's media dir behind all-files access, which
     * cannot be requested inline; the user flips it in Settings (Sesame-M
     * uses the same flow). Returning here triggers {@link #onResume}.
     */
    @TargetApi(Build.VERSION_CODES.R)
    private void requestAllFilesAccess() {
        try {
            if (Environment.isExternalStorageManager()) {
                reload();
                return;
            }
        } catch (RuntimeException ignored) {
            // Fall through to the settings page below.
        }
        Toast.makeText(this, "请允许“管理所有文件”，回退后自动刷新", Toast.LENGTH_LONG).show();
        try {
            Intent appPage = new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            appPage.setData(Uri.parse("package:" + getPackageName()));
            startActivity(appPage);
            return;
        } catch (ActivityNotFoundException ignored) {
            // Some OEMs only expose the shared list; try that next.
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        } catch (ActivityNotFoundException failed) {
            Toast.makeText(this, "无法打开设置页，请手动允许管理所有文件", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            reload();
        }
    }

    private void refreshLevelButtons() {
        char[] levels = {'D', 'I', 'W', 'E'};
        for (int index = 0; index < levelButtons.length; index++) {
            Button button = levelButtons[index];
            if (button == null) {
                continue;
            }
            boolean selected = levels[index] == minLevel;
            button.setBackground(rounded(selected ? PRIMARY : CARD, 10));
            button.setTextColor(selected ? Color.WHITE : TEXT_SECONDARY);
        }
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(TEXT_SECONDARY);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(rounded(CARD, 10));
        return button;
    }

    private Button fileButton(String label, boolean selected) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setBackground(rounded(selected ? PRIMARY : CARD, 10));
        button.setTextColor(selected ? Color.WHITE : TEXT_PRIMARY);
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        return button;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 18, TEXT_PRIMARY);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView text(CharSequence value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + " KB";
        }
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
