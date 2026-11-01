package jp.battery.saver;

import android.view.ContextThemeWrapper;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class OptimizationScreen extends ContextThemeWrapper {
    private static final int BG = 0xFFF4F6F3, SURFACE = Color.WHITE;
    private static final int TEXT = 0xFF17251E, MUTED = 0xFF637168, MINT = 0xFF246B4A;
    private static final int BORDER = 0xFFDDE4DC, SOFT_GREEN = 0xFFE6F0E8;
    private static final int DEFAULT_DURATION_SECONDS = 8;
    private static final int MAX_DURATION_SECONDS = 3600;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> optimizationJob;
    private OptimizationOrbView orb;
    private TextView batteryText, batteryCaption, temperatureText, healthText;
    private TextView remainingTimeText, messageText, lastRunText;
    private TextView screenTitle, progressTitle, progressDescription;
    private LinearLayout scanPanel, progressPanel;
    private Button startButton, resultButton;
    private long displayedRemainingMinutes = -1;
    private RadioGroup scanChoices;
    private RadioButton quickScanOption, fullScanOption;
    private int scanMode = OptimizationEngine.SCAN_QUICK;
    private final View[] batterySegments = new View[20];
    private int mode = OptimizationOrbView.READY;
    // 設定で選択した 0 または 1 を実処理へ渡します。初期値は 1 です。
    private int master_clear_selected = 1;
    private long startedAt, lastRun, animationDuration, progressSeed;
    private OptimizationProgress progress;
    private Dialog settingsDialog;
    private boolean registered, foreground, destroyed;
    private SharedPreferences preferences;
    private View rootView;
    private final Runnable optimizationRequest, closeRequest;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateBattery(intent); }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (destroyed || mode != OptimizationOrbView.RUNNING) return;
            long elapsed = SystemClock.elapsedRealtime() - startedAt;
            float fraction = progress.at(elapsed);
            renderProgress(fraction, elapsed);
            if (elapsed >= animationDuration) runOptimizationAfterAnimation();
            else if (foreground) main.postDelayed(this, Math.min(33, animationDuration - elapsed));
        }
    };

    OptimizationScreen(Context context, Bundle state, Runnable optimizationRequest, Runnable closeRequest) {
        super(context, R.style.AppTheme);
        this.optimizationRequest = optimizationRequest;
        this.closeRequest = closeRequest;
        preferences = getSharedPreferences("battery_saver", MODE_PRIVATE);
        lastRun = preferences.getLong("last_optimization", 0);
        loadOptions();
        progressSeed = ThreadLocalRandom.current().nextLong();
        if (state != null) {
            mode = state.getInt("mode", OptimizationOrbView.READY);
            startedAt = state.getLong("startedAt", 0);
            animationDuration = Math.max(1000, Math.min(MAX_DURATION_SECONDS * 1000L,
                    state.getLong("duration", animationDuration)));
            master_clear_selected = state.getInt("master_clear", master_clear_selected) == 0 ? 0 : 1;
            scanMode = state.getInt("scan_mode", scanMode) == OptimizationEngine.SCAN_FULL
                    ? OptimizationEngine.SCAN_FULL : OptimizationEngine.SCAN_QUICK;
            progressSeed = state.getLong("progressSeed", progressSeed);
        }
        progress = new OptimizationProgress(animationDuration, progressSeed);
        buildScreen();
        renderState();
    }

    View getView() { return rootView; }

    void showMessage(String message) {
        messageText.setText(message);
        messageText.setVisibility(View.VISIBLE);
    }

    void refreshLastRun() {
        lastRun = preferences.getLong("last_optimization", 0);
        renderState();
    }

    private void buildScreen() {
        android.util.DisplayMetrics display = getResources().getDisplayMetrics();
        boolean landscape = display.widthPixels / display.density >= 640;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() |
                        WindowInsets.Type.displayCutout());
                left = safe.left; top = safe.top; right = safe.right; bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout outer = column();
        outer.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        scroll.addView(outer, new ScrollView.LayoutParams(-1, -2));
        LinearLayout content = column();
        content.setPadding(dp(20), dp(landscape ? 8 : 12), dp(20), dp(landscape ? 12 : 24));
        outer.addView(content, new LinearLayout.LayoutParams(-1, -2));
        outer.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int available = Math.min(right - left, dp(landscape ? 1000 : 560));
            if (available > 0 && content.getLayoutParams().width != available) {
                android.view.ViewGroup.LayoutParams params = content.getLayoutParams();
                params.width = available;
                content.setLayoutParams(params);
            }
        });

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView brand = new ImageView(this);
        brand.setImageResource(R.drawable.ic_battery_brand);
        brand.setBackground(shape(Color.BLACK, 10, 0));
        brand.setPadding(dp(5), dp(5), dp(5), dp(5));
        brand.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(brand, lp(32, 32));
        TextView brandText = text(getString(R.string.app_name), 19, TEXT, true);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, -2, 1);
        brandLp.leftMargin = dp(10);
        header.addView(brandText, brandLp);
        if (closeRequest == null) {
            ImageButton settings = new ImageButton(this);
            settings.setImageResource(R.drawable.ic_settings);
            settings.setImageTintList(ColorStateList.valueOf(TEXT));
            settings.setPadding(dp(13), dp(13), dp(13), dp(13));
            settings.setBackground(ripple(BG, 24));
            settings.setContentDescription(getString(R.string.settings_action));
            settings.setOnClickListener(v -> showSettingsDialog());
            header.addView(settings, lp(48, 48));
        } else {
            Button close = actionButton(getString(R.string.close_action), SOFT_GREEN, MINT);
            close.setContentDescription(getString(R.string.close_description));
            close.setOnClickListener(v -> closeRequest.run());
            header.addView(close, new LinearLayout.LayoutParams(-2, -2));
        }
        add(content, header, 0);
        screenTitle = text(getString(R.string.battery_title), 28, TEXT, true);
        screenTitle.setVisibility(landscape ? View.GONE : View.VISIBLE);
        if (android.os.Build.VERSION.SDK_INT >= 28) screenTitle.setAccessibilityHeading(true);
        add(content, screenTitle, 20);

        LinearLayout dashboard = landscape ? row() : column();
        dashboard.setGravity(Gravity.TOP);
        LinearLayout overview = column();
        LinearLayout actions = column();
        if (landscape) {
            LinearLayout.LayoutParams overviewLp = new LinearLayout.LayoutParams(0, -2, 1);
            overviewLp.rightMargin = dp(24);
            dashboard.addView(overview, overviewLp);
            dashboard.addView(actions, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            dashboard.addView(overview, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
            actionsLp.topMargin = dp(28);
            dashboard.addView(actions, actionsLp);
        }
        add(content, dashboard, landscape ? 12 : 20);

        LinearLayout batteryCard = column();
        batteryCard.setPadding(dp(landscape ? 20 : 24), dp(landscape ? 18 : 22),
                dp(landscape ? 20 : 24), dp(landscape ? 18 : 22));
        batteryCard.setBackground(shape(SURFACE, 22, BORDER));
        LinearLayout batteryTop = row();
        batteryTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView batteryLabel = text(getString(R.string.battery_level), 14, MUTED, false);
        batteryTop.addView(batteryLabel, new LinearLayout.LayoutParams(0, -2, 1));
        batteryCaption = text("", 12, MINT, true);
        batteryCaption.setGravity(Gravity.CENTER);
        batteryCaption.setPadding(dp(10), dp(6), dp(10), dp(6));
        batteryCaption.setBackground(shape(SOFT_GREEN, 20, 0));
        batteryTop.addView(batteryCaption);
        batteryCard.addView(batteryTop);
        batteryText = text("— %", 68, TEXT, true);
        batteryText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        batteryText.setMaxLines(1);
        batteryText.setAutoSizeTextTypeUniformWithConfiguration(32, landscape ? 48 : 68, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        add(batteryCard, batteryText, landscape ? 6 : 12);
        LinearLayout segments = row();
        segments.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        for (int i = 0; i < batterySegments.length; i++) {
            View segment = new View(this);
            segment.setBackground(shape(BORDER, 2, 0));
            LinearLayout.LayoutParams segmentLp = new LinearLayout.LayoutParams(0, dp(8), 1);
            if (i > 0) segmentLp.leftMargin = dp(3);
            segments.addView(segment, segmentLp);
            batterySegments[i] = segment;
        }
        add(batteryCard, segments, landscape ? 10 : 14);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.topMargin = dp(landscape ? 14 : 24);
        batteryCard.addView(divider(), dividerLp);
        LinearLayout details = row();
        LinearLayout temp = metric(getString(R.string.battery_temperature));
        temperatureText = text("— °C", 21, TEXT, true);
        temp.addView(temperatureText);
        LinearLayout health = metric(getString(R.string.battery_health));
        healthText = text("—", 21, TEXT, true);
        health.addView(healthText);
        LinearLayout.LayoutParams tempLp = new LinearLayout.LayoutParams(0, -2, 1);
        tempLp.rightMargin = dp(12);
        details.addView(temp, tempLp);
        details.addView(health, new LinearLayout.LayoutParams(0, -2, 1));
        add(batteryCard, details, landscape ? 12 : 20);
        overview.addView(batteryCard, new LinearLayout.LayoutParams(-1, -2));

        scanPanel = column();
        TextView scanHeading = text(getString(R.string.scan_heading), 20, TEXT, true);
        if (android.os.Build.VERSION.SDK_INT >= 28) scanHeading.setAccessibilityHeading(true);
        scanPanel.addView(scanHeading);
        TextView modeLabel = text(getString(R.string.diagnosis_mode), 13, MUTED, false);
        add(scanPanel, modeLabel, landscape ? 8 : 16);
        scanChoices = new RadioGroup(this);
        scanChoices.setOrientation(LinearLayout.VERTICAL);
        scanChoices.setPadding(dp(4), dp(4), dp(4), dp(4));
        scanChoices.setBackground(shape(SURFACE, 18, BORDER));
        quickScanOption = scanOption(R.string.scan_quick, landscape);
        fullScanOption = scanOption(R.string.scan_full, landscape);
        scanChoices.addView(quickScanOption, new RadioGroup.LayoutParams(-1, -2));
        RadioGroup.LayoutParams fullLp = new RadioGroup.LayoutParams(-1, -2);
        fullLp.topMargin = dp(4);
        scanChoices.addView(fullScanOption, fullLp);
        scanChoices.check(scanMode == OptimizationEngine.SCAN_FULL
                ? fullScanOption.getId() : quickScanOption.getId());
        scanChoices.setOnCheckedChangeListener((group, checkedId) -> {
            if (mode == OptimizationOrbView.RUNNING || closeRequest != null) return;
            scanMode = checkedId == fullScanOption.getId()
                    ? OptimizationEngine.SCAN_FULL : OptimizationEngine.SCAN_QUICK;
            updateScanSelection();
            preferences.edit().putInt("scan_mode", scanMode).apply();
        });
        add(scanPanel, scanChoices, 10);
        updateScanSelection();
        TextView scanHelp = text(getString(R.string.scan_help), 13, MUTED, false);
        scanHelp.setLineSpacing(dp(3), 1);
        add(scanPanel, scanHelp, landscape ? 10 : 14);
        startButton = actionButton(getString(R.string.optimize_action), MINT, Color.WHITE);
        startButton.setOnClickListener(v -> {
            if (optimizationRequest != null) optimizationRequest.run();
        });
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, -2);
        startLp.topMargin = dp(landscape ? 12 : 20);
        scanPanel.addView(startButton, startLp);
        lastRunText = text("", 12, MUTED, false);
        lastRunText.setGravity(Gravity.CENTER);
        lastRunText.setLineSpacing(dp(3), 1);
        add(scanPanel, lastRunText, landscape ? 10 : 14);
        actions.addView(scanPanel, new LinearLayout.LayoutParams(-1, -2));

        progressPanel = column();
        progressPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        progressPanel.setPadding(dp(20), dp(20), dp(20), dp(24));
        progressPanel.setBackground(shape(SURFACE, 22, BORDER));
        progressTitle = text("", 20, TEXT, true);
        progressTitle.setGravity(Gravity.CENTER);
        progressTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        progressPanel.addView(progressTitle);
        orb = new OptimizationOrbView(this);
        orb.setRepeatEnabled(false);
        orb.setId(View.generateViewId());
        progressPanel.addView(orb, new LinearLayout.LayoutParams(-1, dp(landscape ? 160 : 200)));
        remainingTimeText = text("", 14, MINT, true);
        remainingTimeText.setGravity(Gravity.CENTER);
        add(progressPanel, remainingTimeText, 0);
        progressDescription = text(getString(R.string.scan_complete_description), 14, MUTED, false);
        progressDescription.setGravity(Gravity.CENTER);
        progressDescription.setLineSpacing(dp(4), 1);
        add(progressPanel, progressDescription, 8);
        resultButton = actionButton(getString(R.string.return_to_settings), MINT, Color.WHITE);
        resultButton.setOnClickListener(v -> {
            if (closeRequest != null) closeRequest.run();
        });
        LinearLayout.LayoutParams resultLp = new LinearLayout.LayoutParams(-1, -2);
        resultLp.topMargin = dp(20);
        progressPanel.addView(resultButton, resultLp);
        actions.addView(progressPanel, new LinearLayout.LayoutParams(-1, -2));

        messageText = text("", 16, TEXT, false);
        messageText.setGravity(Gravity.CENTER);
        messageText.setLineSpacing(dp(5), 1);
        messageText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        messageText.setVisibility(View.GONE);
        add(actions, messageText, 16);
        rootView = root;
        root.requestApplyInsets();
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(BORDER);
        line.setMinimumHeight(dp(1));
        return line;
    }

    private Button actionButton(String label, int background, int foregroundColor) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(foregroundColor);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setAllCaps(false);
        button.setMinHeight(dp(56));
        button.setMinimumHeight(dp(56));
        button.setPadding(dp(20), dp(14), dp(20), dp(14));
        button.setBackground(ripple(background, 14));
        button.setStateListAnimator(null);
        return button;
    }

    private void updateScanSelection() {
        boolean quick = scanMode == OptimizationEngine.SCAN_QUICK;
        quickScanOption.setBackground(ripple(quick ? SOFT_GREEN : SURFACE, 14));
        fullScanOption.setBackground(ripple(quick ? SURFACE : SOFT_GREEN, 14));
        quickScanOption.setTypeface(Typeface.create(quick ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        fullScanOption.setTypeface(Typeface.create(quick ? "sans-serif" : "sans-serif-medium", Typeface.NORMAL));
    }

    private LinearLayout metric(String label) {
        LinearLayout card = column();
        TextView caption = text(label, 12, MUTED, false);
        caption.setPadding(0, 0, 0, dp(6));
        card.addView(caption);
        return card;
    }

    private RadioButton scanOption(int titleRes, boolean compact) {
        RadioButton option = new RadioButton(this);
        option.setId(View.generateViewId());
        option.setText(titleRes);
        option.setTextSize(15);
        option.setTextColor(TEXT);
        option.setIncludeFontPadding(false);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setMinHeight(dp(compact ? 48 : 56));
        option.setPadding(dp(12), dp(compact ? 8 : 12), dp(12), dp(compact ? 8 : 12));
        option.setButtonTintList(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{MINT, MUTED}));
        return option;
    }

    private void loadOptions() {
        master_clear_selected = preferences.getInt("master_clear", 1) == 0 ? 0 : 1;
        scanMode = preferences.getInt("scan_mode", OptimizationEngine.SCAN_QUICK) == OptimizationEngine.SCAN_FULL
                ? OptimizationEngine.SCAN_FULL : OptimizationEngine.SCAN_QUICK;
        int seconds = Math.max(1, Math.min(MAX_DURATION_SECONDS,
                preferences.getInt("optimization_duration_seconds", DEFAULT_DURATION_SECONDS)));
        animationDuration = seconds * 1000L;
    }

    private RadioButton masterClearOption(int label) {
        RadioButton option = new RadioButton(this);
        option.setId(View.generateViewId());
        option.setText(label);
        option.setTextSize(14);
        option.setTextColor(TEXT);
        option.setButtonTintList(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{MINT, MUTED}));
        option.setMinHeight(dp(48));
        option.setLineSpacing(dp(4), 1);
        option.setPadding(0, dp(8), dp(4), dp(8));
        return option;
    }

    private void showSettingsDialog() {
        if (settingsDialog != null) return;
        Dialog dialog = new Dialog(this);
        settingsDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = column();
        panel.setPadding(dp(24), dp(24), dp(24), dp(20));
        panel.setBackground(shape(SURFACE, 24, BORDER));
        panel.addView(text(getString(R.string.settings_action), 20, TEXT, true));
        add(panel, text(getString(R.string.optimization_duration), 14, TEXT, true), 24);
        TextView durationHelp = text(getString(R.string.duration_help), 13, MUTED, false);
        durationHelp.setLineSpacing(dp(4), 1);
        add(panel, durationHelp, 8);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setTextSize(24);
        input.setMinHeight(dp(56));
        input.setBackgroundTintList(ColorStateList.valueOf(MINT));
        input.setContentDescription(getString(R.string.duration_input));
        input.setText(String.valueOf(animationDuration / 1000));
        input.selectAll();
        LinearLayout durationRow = row();
        durationRow.setGravity(Gravity.CENTER_VERTICAL);
        durationRow.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        TextView secondsUnit = text(getString(R.string.seconds_unit), 15, MUTED, false);
        secondsUnit.setPadding(dp(12), 0, 0, 0);
        durationRow.addView(secondsUnit);
        add(panel, durationRow, 12);
        Button advancedToggle = actionButton(getString(R.string.advanced_settings), SURFACE, MINT);
        advancedToggle.setTextSize(14);
        advancedToggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        advancedToggle.setPadding(0, dp(12), 0, dp(12));
        advancedToggle.setMinHeight(dp(48));
        advancedToggle.setBackground(ripple(SURFACE, 10));
        advancedToggle.setFocusable(true);
        add(panel, advancedToggle, 16);
        LinearLayout advanced = column();
        advanced.setVisibility(View.GONE);
        advancedToggle.setOnClickListener(view -> {
            boolean expanded = advanced.getVisibility() == View.VISIBLE;
            advanced.setVisibility(expanded ? View.GONE : View.VISIBLE);
            advancedToggle.setText(expanded ? R.string.advanced_settings : R.string.advanced_settings_open);
        });
        advanced.addView(text(getString(R.string.master_clear_label), 14, TEXT, true));
        RadioGroup choices = new RadioGroup(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        RadioButton one = masterClearOption(R.string.master_clear_one);
        RadioButton zero = masterClearOption(R.string.master_clear_zero);
        choices.addView(one, new RadioGroup.LayoutParams(-1, -2));
        choices.addView(zero, new RadioGroup.LayoutParams(-1, -2));
        choices.check(master_clear_selected == 0 ? zero.getId() : one.getId());
        add(advanced, choices, 8);
        add(panel, advanced, 4);
        boolean stackedActions = getResources().getConfiguration().fontScale > 1.3f;
        LinearLayout actions = stackedActions ? column() : row();
        Button cancel = new Button(this);
        cancel.setText(R.string.cancel_action);
        cancel.setTextColor(TEXT);
        cancel.setAllCaps(false);
        cancel.setMinHeight(dp(48));
        cancel.setStateListAnimator(null);
        cancel.setElevation(0);
        cancel.setBackground(ripple(SOFT_GREEN, 14));
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, stackedActions ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1));
        Button save = new Button(this);
        save.setText(R.string.save_action);
        save.setTextColor(Color.WHITE);
        save.setAllCaps(false);
        save.setMinHeight(dp(48));
        save.setStateListAnimator(null);
        save.setElevation(0);
        save.setBackground(ripple(MINT, 14));
        save.setOnClickListener(view -> {
            int seconds;
            try { seconds = Integer.parseInt(input.getText().toString().trim()); }
            catch (NumberFormatException error) { seconds = 0; }
            if (seconds < 1 || seconds > MAX_DURATION_SECONDS) {
                input.setError(getString(R.string.duration_range));
                return;
            }
            animationDuration = seconds * 1000L;
            master_clear_selected = choices.getCheckedRadioButtonId() == zero.getId() ? 0 : 1;
            preferences.edit().putInt("optimization_duration_seconds", seconds)
                    .putInt("master_clear", master_clear_selected).apply();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams saveLp = stackedActions ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1);
        if (stackedActions) saveLp.topMargin = dp(12);
        else saveLp.leftMargin = dp(12);
        actions.addView(save, saveLp);
        add(panel, actions, 24);
        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.setBackground(shape(SURFACE, 24, 0));
        dialogScroll.addView(panel);
        dialog.setContentView(dialogScroll);
        dialog.setOnDismissListener(ignored -> settingsDialog = null);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.65f);
        }
        dialog.show();
        if (window != null) window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(400)), -2);
    }

    /** 設定起動の前に初期表示を準備。ここではアニメーションを開始しません。 */
    void prepareOptimization() {
        mode = OptimizationOrbView.RUNNING;
        startedAt = 0;
        // 起動時に準備したオーバーレイにも、直前の選択・秒数を反映します。
        loadOptions();
        progressSeed = ThreadLocalRandom.current().nextLong();
        progress = new OptimizationProgress(animationDuration, progressSeed);
        renderState();
    }

    /** WindowManager.addView() の直後に既存の最適化演出を開始します。 */
    void startPreparedOptimization() {
        startedAt = SystemClock.elapsedRealtime();
        startUpdates();
        orb.announceForAccessibility(getString(R.string.optimization_started));
    }

    private void renderState() {
        boolean running = mode == OptimizationOrbView.RUNNING;
        boolean complete = mode == OptimizationOrbView.COMPLETE;
        boolean overlay = closeRequest != null;
        boolean failed = overlay && !running && !complete;
        rootView.setKeepScreenOn(running);
        scanChoices.check(scanMode == OptimizationEngine.SCAN_FULL
                ? fullScanOption.getId() : quickScanOption.getId());
        updateScanSelection();
        quickScanOption.setEnabled(!running && !overlay);
        fullScanOption.setEnabled(!running && !overlay);
        scanPanel.setVisibility(overlay ? View.GONE : View.VISIBLE);
        progressPanel.setVisibility(overlay ? View.VISIBLE : View.GONE);
        screenTitle.setText(overlay ? (failed ? R.string.scan_failed_title : complete ? R.string.scan_complete_title : R.string.scan_running_title)
                : R.string.battery_title);
        progressTitle.setText(failed ? R.string.scan_failed_title : complete ? R.string.optimization_complete : R.string.optimization_progress);
        progressDescription.setText(failed ? R.string.optimization_failed : R.string.scan_complete_description);
        progressDescription.setVisibility(running ? View.GONE : View.VISIBLE);
        resultButton.setVisibility(running ? View.GONE : View.VISIBLE);
        orb.setVisibility(failed ? View.GONE : View.VISIBLE);
        messageText.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) messageText.setText(R.string.keep_screen_open);
        remainingTimeText.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) {
            long elapsed = startedAt == 0 ? 0 : SystemClock.elapsedRealtime() - startedAt;
            renderProgress(progress.at(elapsed), elapsed);
        } else {
            orb.setState(mode, complete ? 1 : 0);
        }
        orb.setClickable(false);
        orb.setFocusable(running);
        startButton.setEnabled(!running);
        lastRunText.setText(lastRun == 0 ? getString(R.string.last_run_empty) : getString(R.string.last_run,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.JAPAN).format(new Date(lastRun))));
    }

    private void renderProgress(float fraction, long elapsedMillis) {
        orb.setState(OptimizationOrbView.RUNNING, fraction);
        long minutes = (progress.remainingSeconds(elapsedMillis) + 59) / 60;
        if (minutes == displayedRemainingMinutes) return;
        displayedRemainingMinutes = minutes;
        if (minutes == 0) {
            remainingTimeText.setText(R.string.scan_finishing);
        } else {
            remainingTimeText.setText(getString(R.string.scan_remaining_minutes, minutes));
        }
    }

    /** 演出の終点。実処理は OptimizationEngine.performOptimization() に追加してください。 */
    private void runOptimizationAfterAnimation() {
        if (optimizationJob != null) return;
        final int selectedScanMode = scanMode;
        final int selectedMasterClear = master_clear_selected;
        optimizationJob = executor.submit(() -> {
            try {
                // 【接続箇所】演出100% → 実処理 → 正常終了 → 完了画面 の順序です。
                OptimizationEngine.performOptimization(getApplicationContext(), selectedMasterClear, selectedScanMode);
                main.post(() -> {
                    if (destroyed) return;
                    optimizationJob = null;
                    mode = OptimizationOrbView.COMPLETE;
                    lastRun = System.currentTimeMillis();
                    preferences.edit().putLong("last_optimization", lastRun).apply();
                    renderState();
                    orb.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30 ?
                HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
                    orb.announceForAccessibility(getString(R.string.optimization_complete));
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (destroyed) return;
                    optimizationJob = null;
                    mode = OptimizationOrbView.READY;
                    renderState();
                    if (closeRequest == null) showMessage(getString(R.string.optimization_failed));
                });
            }
        });
    }

    private void updateBattery(Intent intent) {
        if (intent == null) return;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
        String levelLabel = percent < 0 ? "— %" : percent + " %";
        SpannableString levelText = new SpannableString(levelLabel);
        levelText.setSpan(new RelativeSizeSpan(.42f), levelLabel.length() - 1, levelLabel.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        batteryText.setText(levelText);
        batteryText.setContentDescription(percent < 0 ? "バッテリー残量を取得できません" : "バッテリー残量 " + percent + "パーセント");
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING;
        boolean full = status == BatteryManager.BATTERY_STATUS_FULL;
        batteryCaption.setText(charging ? R.string.battery_charging : full ? R.string.battery_full
                : status == BatteryManager.BATTERY_STATUS_DISCHARGING ? R.string.battery_discharging
                : status == BatteryManager.BATTERY_STATUS_NOT_CHARGING ? R.string.battery_not_charging
                : R.string.battery_status_unknown);
        for (int i = 0; i < batterySegments.length; i++) {
            boolean filled = percent >= 0 && i < Math.round(percent / 5f);
            batterySegments[i].setBackground(shape(filled ? (percent <= 20 ? 0xFFA56724 : MINT) : BORDER, 2, 0));
        }
        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        temperatureText.setText(temperature == Integer.MIN_VALUE ? "— °C" : String.format(Locale.JAPAN, "%.1f °C", temperature / 10f));
        int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
        String healthLabel;
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: healthLabel = getString(R.string.battery_health_good); break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: healthLabel = "高温"; break;
            case BatteryManager.BATTERY_HEALTH_COLD: healthLabel = "低温"; break;
            case BatteryManager.BATTERY_HEALTH_DEAD: healthLabel = "要確認"; break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: healthLabel = "過電圧"; break;
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: healthLabel = "要確認"; break;
            default: healthLabel = "情報なし";
        }
        healthText.setText(healthLabel);
        boolean warning = health != BatteryManager.BATTERY_HEALTH_GOOD
                && health != BatteryManager.BATTERY_HEALTH_UNKNOWN;
        healthText.setTextColor(warning ? 0xFF99531D : health == BatteryManager.BATTERY_HEALTH_GOOD ? MINT : MUTED);
    }

    void startUpdates() {
        if (foreground || destroyed) return;
        foreground = true;
        orb.setForeground(true);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        } else registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registered = true;
        if (mode == OptimizationOrbView.RUNNING) main.post(ticker);
    }

    void stopUpdates() {
        foreground = false;
        orb.setForeground(false);
        main.removeCallbacks(ticker);
        if (registered) { unregisterReceiver(batteryReceiver); registered = false; }
    }

    void saveState(Bundle out) {
        out.putInt("mode", mode);
        out.putLong("startedAt", startedAt);
        out.putLong("duration", animationDuration);
        out.putInt("master_clear", master_clear_selected);
        out.putInt("scan_mode", scanMode);
        out.putLong("progressSeed", progressSeed);
    }

    void release() {
        if (settingsDialog != null) settingsDialog.dismiss();
        rootView.setKeepScreenOn(false);
        stopUpdates();
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        if (optimizationJob != null) optimizationJob.cancel(true);
        executor.shutdownNow();
    }

    private TextView text(String value, float size, int color, boolean medium) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return view;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(dp(width), dp(height)); }
    private void add(LinearLayout parent, View view, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topMargin);
        parent.addView(view, params);
    }
    private GradientDrawable shape(int color, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (border != 0) drawable.setStroke(dp(1), border);
        return drawable;
    }
    private android.graphics.drawable.RippleDrawable ripple(int color, int radius) {
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22246B4A),
                shape(color, radius, 0), shape(Color.WHITE, radius, 0));
    }
}
