package com.zz.douyin;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.service.XposedService;

public final class MainActivity extends Activity
        implements ModuleApplication.ServiceStateListener {
    private static final int BACKGROUND = Color.rgb(16, 17, 20);
    private static final int CARD = Color.rgb(28, 29, 34);
    private static final int PRIMARY = Color.rgb(254, 44, 85);
    private static final int TEXT_PRIMARY = Color.rgb(245, 245, 248);
    private static final int TEXT_SECONDARY = Color.rgb(169, 171, 180);

    private TextView serviceStatus;
    private Switch moduleEnabled;
    private Switch blockDoubleTap;
    private Switch immersiveEnabled;
    private Switch skipAds;
    private Switch skipImages;
    private Switch skipLives;
    private Switch skipVideos;
    private Switch skipLongVideos;
    private EditText longThresholdInput;
    private Button saveLongThreshold;
    private Switch showDanmaku;
    private Switch autoNext;
    private Switch exactCounts;
    private Switch publishTime;
    private Switch publishLocation;
    private Switch customColors;
    private Switch copyLink;
    private Switch showProgress;
    private Switch hidePublish;
    private EditText hideTabsInput;
    private Button saveHideTabs;
    private EditText countColorInput;
    private EditText timeColorInput;
    private EditText locationColorInput;
    private Button saveColors;
    private EditText keywordInput;
    private Button saveKeywords;
    private SharedPreferences preferences;
    private SharedPreferences localPreferences;
    private boolean usingRemote;
    private final Set<String> offlineDirty = new HashSet<>();
    private boolean loading = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        localPreferences = getSharedPreferences(
                FilterPreferences.NAME, MODE_PRIVATE);
        setContentView(buildContent());
        bindPreferences(null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ModuleApplication.addServiceStateListener(this, true);
    }

    @Override
    protected void onStop() {
        ModuleApplication.removeServiceStateListener(this);
        super.onStop();
    }

    @Override
    public void onServiceStateChanged(XposedService service) {
        runOnUiThread(() -> bindPreferences(service));
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(32), dp(20), dp(32));
        scroll.addView(root, matchWrap());

        TextView title = text("抖仙人", 28, TEXT_PRIMARY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView subtitle = text("播放与内容设置", 15, PRIMARY);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(4);
        root.addView(subtitle, subtitleParams);

        serviceStatus = text("正在连接 LSPosed 服务…", 13, TEXT_SECONDARY);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(16);
        root.addView(serviceStatus, statusParams);

        LinearLayout masterCard = card();
        moduleEnabled = addSwitch(masterCard, "功能总开关",
                "关闭后恢复抖音原有界面和操作，所有模块功能停止生效",
                FilterPreferences.KEY_MODULE_ENABLED);
        LinearLayout.LayoutParams masterParams = matchWrap();
        masterParams.topMargin = dp(20);
        root.addView(masterCard, masterParams);

        TextView typeHeader = sectionTitle("跳过类型");
        LinearLayout.LayoutParams typeHeaderParams = matchWrap();
        typeHeaderParams.topMargin = dp(28);
        root.addView(typeHeader, typeHeaderParams);

        LinearLayout typeCard = card();
        skipAds = addSwitch(
                typeCard,
                "广告",
                "跳过当前模型明确标记的广告",
                FilterPreferences.KEY_SKIP_ADS
        );
        addDivider(typeCard);
        skipImages = addSwitch(
                typeCard,
                "图文",
                "跳过图集、长文章及其他非视频内容",
                FilterPreferences.KEY_SKIP_IMAGES
        );
        addDivider(typeCard);
        skipLives = addSwitch(
                typeCard,
                "直播",
                "推荐页跳过直播间；在直播 tab 内不跳过",
                FilterPreferences.KEY_SKIP_LIVES
        );
        addDivider(typeCard);
        skipVideos = addSwitch(
                typeCard,
                "视频",
                "跳过全部普通视频；关闭时仍应用关键词过滤",
                FilterPreferences.KEY_SKIP_VIDEOS
        );
        addDivider(typeCard);
        skipLongVideos = addSwitch(
                typeCard,
                "长视频",
                "跳过时长超过下方阈值的普通视频；取不到时长时放行",
                FilterPreferences.KEY_SKIP_LONG_VIDEOS
        );
        longThresholdInput = addLabeledField(typeCard, "长视频阈值（秒）");
        saveLongThreshold = new Button(this);
        saveLongThreshold.setText("保存阈值");
        saveLongThreshold.setTextColor(Color.WHITE);
        saveLongThreshold.setTextSize(15);
        saveLongThreshold.setAllCaps(false);
        saveLongThreshold.setBackground(rounded(PRIMARY, 12));
        saveLongThreshold.setOnClickListener(view -> saveLongThresholdSettings());
        LinearLayout.LayoutParams saveLongThresholdParams = matchWrap();
        saveLongThresholdParams.topMargin = dp(14);
        saveLongThresholdParams.height = dp(48);
        typeCard.addView(saveLongThreshold, saveLongThresholdParams);
        LinearLayout.LayoutParams typeCardParams = matchWrap();
        typeCardParams.topMargin = dp(10);
        root.addView(typeCard, typeCardParams);

        TextView playbackHeader = sectionTitle("播放设置");
        LinearLayout.LayoutParams playbackHeaderParams = matchWrap();
        playbackHeaderParams.topMargin = dp(28);
        root.addView(playbackHeader, playbackHeaderParams);

        LinearLayout playbackCard = card();
        immersiveEnabled = addSwitch(playbackCard, "沉浸式播放",
                "播放时隐藏界面，暂停时恢复；关闭后仍可使用过滤、双击拦截和下载",
                FilterPreferences.KEY_IMMERSIVE_ENABLED);
        addDivider(playbackCard);
        showDanmaku = addSwitch(
                playbackCard,
                "播放弹幕",
                "纯净播放时保留抖音原生弹幕，其他界面仍保持隐藏",
                FilterPreferences.KEY_SHOW_DANMAKU
        );
        addDivider(playbackCard);
        blockDoubleTap = addSwitch(playbackCard, "禁用屏幕双击",
                "拦截视频画面双击，保留单击暂停、滑动和侧边长按",
                FilterPreferences.KEY_BLOCK_DOUBLE_TAP);
        addDivider(playbackCard);
        autoNext = addSwitch(playbackCard, "播放完成自动下一条",
                "当前视频播完后自动上滑到下一条；关闭后停在末尾",
                FilterPreferences.KEY_AUTO_NEXT);
        addDivider(playbackCard);
        exactCounts = addSwitch(playbackCard, "精确显示互动数",
                "点赞/评论/收藏显示完整数字，不再缩写为“万”",
                FilterPreferences.KEY_EXACT_COUNTS);
        addDivider(playbackCard);
        publishTime = addSwitch(playbackCard, "一直显示发布时间",
                "在视频描述下方单行显示发布时间，精确到分钟",
                FilterPreferences.KEY_PUBLISH_TIME);
        addDivider(playbackCard);
        publishLocation = addSwitch(playbackCard, "一直显示IP属地/地点",
                "在同一行追加IP属地与地点；无数据时不显示、不伪造",
                FilterPreferences.KEY_PUBLISH_LOCATION);
        addDivider(playbackCard);
        customColors = addSwitch(playbackCard, "自定义文本颜色",
                "用下方颜色覆盖精确数字/发布时间/IP属地地点；关闭后恢复抖音原样式",
                FilterPreferences.KEY_CUSTOM_TEXT_COLORS);
        addDivider(playbackCard);
        copyLink = addSwitch(playbackCard, "复制链接按钮",
                "暂停时在下载按钮旁显示，一键复制当前视频无水印直链",
                FilterPreferences.KEY_COPY_LINK);
        addDivider(playbackCard);
        showProgress = addSwitch(playbackCard, "常显播放进度条",
                "沉浸播放时保留底部视频进度条，不随其他界面隐藏",
                FilterPreferences.KEY_SHOW_PROGRESS);
        LinearLayout.LayoutParams playbackCardParams = matchWrap();
        playbackCardParams.topMargin = dp(10);
        root.addView(playbackCard, playbackCardParams);

        TextView colorHeader = sectionTitle("文本颜色");
        LinearLayout.LayoutParams colorHeaderParams = matchWrap();
        colorHeaderParams.topMargin = dp(28);
        root.addView(colorHeader, colorHeaderParams);

        LinearLayout colorCard = card();
        TextView colorHint = text(
                "每行一个十六进制颜色，例如 #FF5722；支持 #RGB、#RRGGBB、#AARRGGBB。",
                14,
                TEXT_SECONDARY
        );
        colorHint.setLineSpacing(0, 1.25f);
        colorCard.addView(colorHint, matchWrap());
        countColorInput = addLabeledField(colorCard, "精确数字颜色");
        timeColorInput = addLabeledField(colorCard, "发布时间颜色");
        locationColorInput = addLabeledField(colorCard, "IP属地/地点颜色");

        saveColors = new Button(this);
        saveColors.setText("保存颜色");
        saveColors.setTextColor(Color.WHITE);
        saveColors.setTextSize(15);
        saveColors.setAllCaps(false);
        saveColors.setBackground(rounded(PRIMARY, 12));
        saveColors.setOnClickListener(view -> saveColorSettings());
        LinearLayout.LayoutParams saveColorsParams = matchWrap();
        saveColorsParams.topMargin = dp(14);
        saveColorsParams.height = dp(48);
        colorCard.addView(saveColors, saveColorsParams);

        LinearLayout.LayoutParams colorCardParams = matchWrap();
        colorCardParams.topMargin = dp(10);
        root.addView(colorCard, colorCardParams);

        TextView uiHeader = sectionTitle("界面元素");
        LinearLayout.LayoutParams uiHeaderParams = matchWrap();
        uiHeaderParams.topMargin = dp(28);
        root.addView(uiHeader, uiHeaderParams);

        LinearLayout uiCard = card();
        hidePublish = addSwitch(uiCard, "隐藏底部发布按钮",
                "隐藏底部中央的“+”发布按钮；识别失败时不隐藏任何元素",
                FilterPreferences.KEY_HIDE_PUBLISH);
        addDivider(uiCard);
        TextView hideTabsHint = text(
                "顶部 TAB 隐藏关键词：命中任意词的顶部短文本会被隐藏。支持换行、逗号或分号分隔，留空即不隐藏。",
                14,
                TEXT_SECONDARY
        );
        hideTabsHint.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams hideTabsHintParams = matchWrap();
        hideTabsHintParams.topMargin = dp(14);
        uiCard.addView(hideTabsHint, hideTabsHintParams);

        hideTabsInput = new EditText(this);
        hideTabsInput.setTextColor(TEXT_PRIMARY);
        hideTabsInput.setHintTextColor(Color.rgb(112, 114, 123));
        hideTabsInput.setTextSize(16);
        hideTabsInput.setHint("例如：商城\n精选");
        hideTabsInput.setGravity(Gravity.TOP | Gravity.START);
        hideTabsInput.setMinLines(2);
        hideTabsInput.setMaxLines(5);
        hideTabsInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        GradientDrawable hideTabsBackground = rounded(Color.rgb(38, 39, 45), 12);
        hideTabsBackground.setStroke(dp(1), Color.rgb(58, 60, 68));
        hideTabsInput.setBackground(hideTabsBackground);
        hideTabsInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams hideTabsInputParams = matchWrap();
        hideTabsInputParams.topMargin = dp(14);
        uiCard.addView(hideTabsInput, hideTabsInputParams);

        saveHideTabs = new Button(this);
        saveHideTabs.setText("保存 TAB 关键词");
        saveHideTabs.setTextColor(Color.WHITE);
        saveHideTabs.setTextSize(15);
        saveHideTabs.setAllCaps(false);
        saveHideTabs.setBackground(rounded(PRIMARY, 12));
        saveHideTabs.setOnClickListener(view -> saveHideTabsSettings());
        LinearLayout.LayoutParams saveHideTabsParams = matchWrap();
        saveHideTabsParams.topMargin = dp(14);
        saveHideTabsParams.height = dp(48);
        uiCard.addView(saveHideTabs, saveHideTabsParams);

        LinearLayout.LayoutParams uiCardParams = matchWrap();
        uiCardParams.topMargin = dp(10);
        root.addView(uiCard, uiCardParams);

        TextView keywordHeader = sectionTitle("视频关键词");
        LinearLayout.LayoutParams keywordHeaderParams = matchWrap();
        keywordHeaderParams.topMargin = dp(28);
        root.addView(keywordHeader, keywordHeaderParams);

        LinearLayout keywordCard = card();
        TextView keywordHint = text(
                "普通视频的标题或介绍包含任意关键词时自动跳过。忽略英文大小写，支持换行、逗号或分号分隔。",
                14,
                TEXT_SECONDARY
        );
        keywordHint.setLineSpacing(0, 1.25f);
        keywordCard.addView(keywordHint, matchWrap());

        keywordInput = new EditText(this);
        keywordInput.setTextColor(TEXT_PRIMARY);
        keywordInput.setHintTextColor(Color.rgb(112, 114, 123));
        keywordInput.setTextSize(16);
        keywordInput.setHint("例如：游戏推广\n不感兴趣\n带货");
        keywordInput.setGravity(Gravity.TOP | Gravity.START);
        keywordInput.setMinLines(4);
        keywordInput.setMaxLines(8);
        keywordInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        GradientDrawable inputBackground = rounded(Color.rgb(38, 39, 45), 12);
        inputBackground.setStroke(dp(1), Color.rgb(58, 60, 68));
        keywordInput.setBackground(inputBackground);
        keywordInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.topMargin = dp(14);
        keywordCard.addView(keywordInput, inputParams);

        saveKeywords = new Button(this);
        saveKeywords.setText("保存关键词");
        saveKeywords.setTextColor(Color.WHITE);
        saveKeywords.setTextSize(15);
        saveKeywords.setAllCaps(false);
        saveKeywords.setBackground(rounded(PRIMARY, 12));
        saveKeywords.setOnClickListener(view -> saveKeywordSettings());
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(14);
        buttonParams.height = dp(48);
        keywordCard.addView(saveKeywords, buttonParams);

        LinearLayout.LayoutParams keywordCardParams = matchWrap();
        keywordCardParams.topMargin = dp(10);
        root.addView(keywordCard, keywordCardParams);

        TextView advancedHeader = sectionTitle("高级");
        LinearLayout.LayoutParams advancedHeaderParams = matchWrap();
        advancedHeaderParams.topMargin = dp(28);
        root.addView(advancedHeader, advancedHeaderParams);

        LinearLayout advancedCard = card();
        Button viewLogs = new Button(this);
        viewLogs.setText("查看运行日志");
        viewLogs.setTextColor(Color.WHITE);
        viewLogs.setTextSize(15);
        viewLogs.setAllCaps(false);
        viewLogs.setBackground(rounded(PRIMARY, 12));
        viewLogs.setOnClickListener(view -> startActivity(
                new Intent(this, LogViewerActivity.class)
        ));
        LinearLayout.LayoutParams viewLogsParams = matchWrap();
        viewLogsParams.topMargin = dp(8);
        advancedCard.addView(viewLogs, viewLogsParams);
        LinearLayout.LayoutParams advancedCardParams = matchWrap();
        advancedCardParams.topMargin = dp(10);
        root.addView(advancedCard, advancedCardParams);

        TextView footer = text(
                "设置保存后会同步给抖音进程。若抖音已在后台运行但未立即生效，请强制停止后重新打开。",
                13,
                TEXT_SECONDARY
        );
        footer.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams footerParams = matchWrap();
        footerParams.topMargin = dp(20);
        root.addView(footer, footerParams);
        return scroll;
    }

    private void bindPreferences(XposedService service) {
        loading = true;
        if (service == null) {
            usingRemote = false;
            preferences = localPreferences;
            refreshControls();
            serviceStatus.setText("未连接 LSPosed 服务，修改先存本机，连接后自动同步");
            serviceStatus.setTextColor(Color.rgb(255, 166, 77));
            setControlsEnabled(true);
            loading = false;
            return;
        }

        SharedPreferences remote = null;
        try {
            remote = service.getRemotePreferences(FilterPreferences.NAME);
        } catch (RuntimeException failed) {
            remote = null;
        }
        if (remote != null && !offlineDirty.isEmpty()) {
            pushOfflineEdits(remote);
            Toast.makeText(this,
                    "离线修改已同步（" + offlineDirty.size() + " 项）",
                    Toast.LENGTH_SHORT).show();
            offlineDirty.clear();
        }
        usingRemote = remote != null;
        preferences = usingRemote ? remote : localPreferences;
        refreshControls();
        if (!usingRemote) {
            serviceStatus.setText("服务异常，修改先存本机，恢复后自动同步");
            serviceStatus.setTextColor(Color.rgb(255, 166, 77));
            setControlsEnabled(true);
            loading = false;
            return;
        }
        serviceStatus.setText(
                "已连接 " + service.getFrameworkName()
                        + " · API " + service.getApiVersion()
        );
        serviceStatus.setTextColor(Color.rgb(84, 214, 142));
        setControlsEnabled(true);
        loading = false;
    }

    private void pushOfflineEdits(SharedPreferences remote) {
        SharedPreferences.Editor editor = remote.edit();
        if (editor == null) {
            return;
        }
        for (String key : offlineDirty) {
            copyKey(localPreferences, editor, key);
        }
        editor.apply();
    }

    private void afterSave(String... keys) {
        if (!usingRemote) {
            for (String key : keys) {
                offlineDirty.add(key);
            }
            return;
        }
        SharedPreferences.Editor mirror = localPreferences.edit();
        if (mirror == null) {
            return;
        }
        for (String key : keys) {
            copyKey(preferences, mirror, key);
        }
        mirror.apply();
    }

    private static void copyKey(
            SharedPreferences from,
            SharedPreferences.Editor to,
            String key
    ) {
        if (from == null || to == null || key == null || !from.contains(key)) {
            return;
        }
        switch (key) {
            case FilterPreferences.KEY_VIDEO_KEYWORDS:
            case FilterPreferences.KEY_HIDE_TABS:
                to.putString(key, from.getString(key, ""));
                break;
            case FilterPreferences.KEY_COUNT_TEXT_COLOR:
            case FilterPreferences.KEY_PUBLISH_TIME_COLOR:
            case FilterPreferences.KEY_LOCATION_TEXT_COLOR:
                to.putInt(key, from.getInt(key, -1));
                break;
            case FilterPreferences.KEY_LONG_VIDEO_THRESHOLD_MS:
                to.putLong(key, from.getLong(
                        key,
                        FilterPreferences.DEFAULT_LONG_VIDEO_THRESHOLD_MS
                ));
                break;
            default:
                to.putBoolean(key, from.getBoolean(key, false));
                break;
        }
    }

    private void refreshControls() {
        SharedPreferences bound = preferences;
        moduleEnabled.setChecked(FilterPreferences.readModuleEnabled(bound));
        immersiveEnabled.setChecked(FilterPreferences.readImmersiveEnabled(bound));
        blockDoubleTap.setChecked(FilterPreferences.readBlockDoubleTap(bound));
        FilterPreferences.Values values = FilterPreferences.read(bound);
        skipAds.setChecked(values.skipAds);
        skipImages.setChecked(values.skipImages);
        skipLives.setChecked(values.skipLives);
        skipVideos.setChecked(values.skipVideos);
        skipLongVideos.setChecked(values.skipLongVideos);
        longThresholdInput.setText(
                String.valueOf(values.longVideoThresholdMs / 1000L));
        showDanmaku.setChecked(FilterPreferences.readShowDanmaku(bound));
        autoNext.setChecked(FilterPreferences.readAutoNext(bound));
        exactCounts.setChecked(FilterPreferences.readExactCounts(bound));
        publishTime.setChecked(FilterPreferences.readPublishTime(bound));
        publishLocation.setChecked(FilterPreferences.readPublishLocation(bound));
        customColors.setChecked(FilterPreferences.readCustomTextColors(bound));
        copyLink.setChecked(FilterPreferences.readCopyLink(bound));
        showProgress.setChecked(FilterPreferences.readShowProgress(bound));
        hidePublish.setChecked(FilterPreferences.readHidePublish(bound));
        hideTabsInput.setText(FilterPreferences.readHideTabs(bound));
        hideTabsInput.setSelection(hideTabsInput.length());
        countColorInput.setText(
                com.zz.douyin.hook.FeedUiStyle.toHex(
                        FilterPreferences.readCountTextColor(bound)));
        timeColorInput.setText(
                com.zz.douyin.hook.FeedUiStyle.toHex(
                        FilterPreferences.readPublishTimeColor(bound)));
        locationColorInput.setText(
                com.zz.douyin.hook.FeedUiStyle.toHex(
                        FilterPreferences.readLocationTextColor(bound)));
        keywordInput.setText(values.keywordText);
        keywordInput.setSelection(keywordInput.length());
    }

    private Switch addSwitch(
            LinearLayout parent,
            String label,
            String description,
            String preferenceKey
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(12), dp(14));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = text(label, 17, TEXT_PRIMARY);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(labelView, matchWrap());
        TextView descriptionView = text(description, 13, TEXT_SECONDARY);
        descriptionView.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams descriptionParams = matchWrap();
        descriptionParams.topMargin = dp(3);
        copy.addView(descriptionView, descriptionParams);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setContentDescription(label);
        toggle.setShowText(false);
        toggle.setButtonTintList(null);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!loading) {
                saveBoolean(preferenceKey, checked);
            }
        });
        row.addView(toggle, wrapWrap());
        parent.addView(row, matchWrap());
        return toggle;
    }

    private void saveBoolean(String key, boolean value) {
        SharedPreferences current = preferences;
        if (current == null) {
            return;
        }
        SharedPreferences.Editor editor = current.edit();
        if (editor != null) {
            editor.putBoolean(key, value).apply();
            afterSave(key);
        }
    }

    private void saveLongThresholdSettings() {
        SharedPreferences current = preferences;
        if (current == null) {
            Toast.makeText(this, "未连接 LSPosed 服务", Toast.LENGTH_SHORT).show();
            return;
        }
        long seconds;
        try {
            seconds = Long.parseLong(longThresholdInput.getText().toString().trim());
        } catch (NumberFormatException failed) {
            seconds = -1L;
        }
        if (seconds < 5L || seconds > 7200L) {
            Toast.makeText(this, "阈值无效，请输入 5~7200 秒", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor editor = current.edit();
        if (editor == null) {
            Toast.makeText(this, "设置保存失败", Toast.LENGTH_SHORT).show();
            return;
        }
        editor.putLong(
                FilterPreferences.KEY_LONG_VIDEO_THRESHOLD_MS, seconds * 1000L).apply();
        afterSave(FilterPreferences.KEY_LONG_VIDEO_THRESHOLD_MS);
        Toast.makeText(this, savedMessage("阈值已保存"), Toast.LENGTH_SHORT).show();
    }

    private void saveHideTabsSettings() {
        SharedPreferences current = preferences;
        if (current == null) {
            Toast.makeText(this, "未连接 LSPosed 服务", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor editor = current.edit();
        if (editor == null) {
            Toast.makeText(this, "设置保存失败", Toast.LENGTH_SHORT).show();
            return;
        }
        editor.putString(
                FilterPreferences.KEY_HIDE_TABS,
                hideTabsInput.getText().toString().trim()
        ).apply();
        afterSave(FilterPreferences.KEY_HIDE_TABS);
        Toast.makeText(this, savedMessage("TAB 关键词已保存"), Toast.LENGTH_SHORT).show();
    }

    private void saveKeywordSettings() {
        SharedPreferences current = preferences;
        if (current == null) {
            Toast.makeText(this, "未连接 LSPosed 服务", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor editor = current.edit();
        if (editor == null) {
            Toast.makeText(this, "设置保存失败", Toast.LENGTH_SHORT).show();
            return;
        }
        editor.putString(
                FilterPreferences.KEY_VIDEO_KEYWORDS,
                keywordInput.getText().toString().trim()
        ).apply();
        afterSave(FilterPreferences.KEY_VIDEO_KEYWORDS);
        Toast.makeText(this, savedMessage("关键词已保存"), Toast.LENGTH_SHORT).show();
    }

    private void setControlsEnabled(boolean enabled) {
        moduleEnabled.setEnabled(enabled);
        blockDoubleTap.setEnabled(enabled);
        immersiveEnabled.setEnabled(enabled);
        skipAds.setEnabled(enabled);
        skipImages.setEnabled(enabled);
        skipLives.setEnabled(enabled);
        skipVideos.setEnabled(enabled);
        skipLongVideos.setEnabled(enabled);
        longThresholdInput.setEnabled(enabled);
        saveLongThreshold.setEnabled(enabled);
        saveLongThreshold.setAlpha(enabled ? 1f : 0.45f);
        showDanmaku.setEnabled(enabled);
        autoNext.setEnabled(enabled);
        exactCounts.setEnabled(enabled);
        publishTime.setEnabled(enabled);
        publishLocation.setEnabled(enabled);
        customColors.setEnabled(enabled);
        copyLink.setEnabled(enabled);
        showProgress.setEnabled(enabled);
        hidePublish.setEnabled(enabled);
        hideTabsInput.setEnabled(enabled);
        saveHideTabs.setEnabled(enabled);
        saveHideTabs.setAlpha(enabled ? 1f : 0.45f);
        countColorInput.setEnabled(enabled);
        timeColorInput.setEnabled(enabled);
        locationColorInput.setEnabled(enabled);
        saveColors.setEnabled(enabled);
        saveColors.setAlpha(enabled ? 1f : 0.45f);
        keywordInput.setEnabled(enabled);
        saveKeywords.setEnabled(enabled);
        saveKeywords.setAlpha(enabled ? 1f : 0.45f);
    }

    private EditText addLabeledField(LinearLayout parent, String label) {
        TextView labelView = text(label, 14, TEXT_PRIMARY);
        LinearLayout.LayoutParams labelParams = matchWrap();
        labelParams.topMargin = dp(14);
        parent.addView(labelView, labelParams);
        EditText input = new EditText(this);
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(Color.rgb(112, 114, 123));
        input.setTextSize(16);
        input.setHint("#FFFFFF");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        GradientDrawable inputBackground = rounded(Color.rgb(38, 39, 45), 12);
        inputBackground.setStroke(dp(1), Color.rgb(58, 60, 68));
        input.setBackground(inputBackground);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.topMargin = dp(8);
        parent.addView(input, inputParams);
        return input;
    }

    private void saveColorSettings() {
        SharedPreferences current = preferences;
        if (current == null) {
            Toast.makeText(this, "未连接 LSPosed 服务", Toast.LENGTH_SHORT).show();
            return;
        }
        String countHex = countColorInput.getText().toString().trim();
        String timeHex = timeColorInput.getText().toString().trim();
        String locationHex = locationColorInput.getText().toString().trim();
        if (!com.zz.douyin.hook.FeedUiStyle.isValidColor(countHex)
                || !com.zz.douyin.hook.FeedUiStyle.isValidColor(timeHex)
                || !com.zz.douyin.hook.FeedUiStyle.isValidColor(locationHex)) {
            Toast.makeText(this, "颜色格式无效，请用 #RRGGBB", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor editor = current.edit();
        if (editor == null) {
            Toast.makeText(this, "设置保存失败", Toast.LENGTH_SHORT).show();
            return;
        }
        editor.putInt(
                FilterPreferences.KEY_COUNT_TEXT_COLOR,
                com.zz.douyin.hook.FeedUiStyle.parseColor(
                        countHex, FilterPreferences.DEFAULT_COUNT_TEXT_COLOR));
        editor.putInt(
                FilterPreferences.KEY_PUBLISH_TIME_COLOR,
                com.zz.douyin.hook.FeedUiStyle.parseColor(
                        timeHex, FilterPreferences.DEFAULT_PUBLISH_TIME_COLOR));
        editor.putInt(
                FilterPreferences.KEY_LOCATION_TEXT_COLOR,
                com.zz.douyin.hook.FeedUiStyle.parseColor(
                        locationHex, FilterPreferences.DEFAULT_LOCATION_TEXT_COLOR));
        editor.apply();
        afterSave(
                FilterPreferences.KEY_COUNT_TEXT_COLOR,
                FilterPreferences.KEY_PUBLISH_TIME_COLOR,
                FilterPreferences.KEY_LOCATION_TEXT_COLOR);
        Toast.makeText(this, savedMessage("颜色已保存"), Toast.LENGTH_SHORT).show();
    }

    private String savedMessage(String saved) {
        return usingRemote ? saved : saved + "（连接后同步）";
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(2), dp(2), dp(2), dp(2));
        layout.setBackground(rounded(CARD, 16));
        return layout;
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(47, 48, 55));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        params.leftMargin = dp(16);
        params.rightMargin = dp(16);
        parent.addView(divider, params);
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

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
