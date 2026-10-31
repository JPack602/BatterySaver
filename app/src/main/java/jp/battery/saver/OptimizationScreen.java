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
import android.text.style.ForegroundColorSpan;
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
    private static final int BG = 0xFF0B1419, SURFACE = 0xFF142128;
    private static final int TEXT = 0xFFF1F7F5, MUTED = 0xFF8EA6A4, MINT = 0xFF72E8BD;
    private static final int DEFAULT_DURATION_SECONDS = 8;
    private static final int MAX_DURATION_SECONDS = 3600;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> optimizationJob;
    private OptimizationOrbView orb;
    private TextView batteryText, batteryCaption, temperatureText, healthText;
    private TextView messageText, lastRunText;
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
            renderProgress(fraction);
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
        boolean landscape = display.widthPixels > display.heightPixels;
        boolean compactPortrait = !landscape && display.heightPixels / display.density < 760;
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
        LinearLayout outer = new LinearLayout(this);
        outer.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        scroll.addView(outer, new ScrollView.LayoutParams(-1, -2));
        LinearLayout content = column();
        content.setGravity(landscape ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        content.setPadding(dp(24), dp(landscape ? 12 : compactPortrait ? 16 : 24),
                dp(24), dp(landscape ? 12 : compactPortrait ? 16 : 24));
        outer.addView(content, new LinearLayout.LayoutParams(-1, -2));
        // 縦画面は上からカードを配置し、文字拡大時や高さ不足ではスクロールできます。
        scroll.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int availableHeight = bottom - top;
            if (content.getMinimumHeight() != availableHeight) content.setMinimumHeight(availableHeight);
        });
        // ノッチ・ナビゲーション領域を除いた実際の横幅に合わせます。
        outer.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int available = Math.min(right - left, dp(landscape ? 920 : 520));
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
        brand.setBackground(shape(Color.BLACK, 14, 0));
        brand.setPadding(dp(7), dp(7), dp(7), dp(7));
        brand.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(brand, lp(42, 42));
        TextView brandText = text(getString(R.string.app_name), 20, TEXT, true);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, -2, 1);
        brandLp.leftMargin = dp(12);
        header.addView(brandText, brandLp);
        if (closeRequest == null) {
            ImageButton settings = new ImageButton(this);
            settings.setImageResource(R.drawable.ic_settings);
            settings.setImageTintList(ColorStateList.valueOf(MINT));
            settings.setPadding(dp(12), dp(12), dp(12), dp(12));
            settings.setBackground(ripple(SURFACE, 16));
            settings.setContentDescription(getString(R.string.settings_action));
            settings.setOnClickListener(v -> showSettingsDialog());
            header.addView(settings, lp(48, 48));
        } else {
            TextView close = text(getString(R.string.close_action), 13, MINT, true);
            close.setGravity(Gravity.CENTER);
            close.setBackground(ripple(SURFACE, 18));
            close.setFocusable(true);
            close.setContentDescription(getString(R.string.close_description));
            close.setOnClickListener(v -> closeRequest.run());
            header.addView(close, lp(64, 48));
        }
        add(content, header, 0);

        // バッテリー情報・診断モード・主操作の順に、一定の間隔で並べます。
        LinearLayout dashboard = landscape ? row() : column();
        LinearLayout overview = column();
        LinearLayout hero = column();
        hero.setGravity(Gravity.CENTER);
        if (landscape) {
            dashboard.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams overviewLp = new LinearLayout.LayoutParams(0, -2, 1);
            overviewLp.rightMargin = dp(16);
            dashboard.addView(overview, overviewLp);
            dashboard.addView(hero, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            dashboard.addView(overview, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
            heroLp.topMargin = dp(compactPortrait ? 20 : 24);
            dashboard.addView(hero, heroLp);
        }
        LinearLayout.LayoutParams dashboardLp = new LinearLayout.LayoutParams(-1, -2);
        dashboardLp.topMargin = dp(landscape ? 12 : compactPortrait ? 12 : 16);
        content.addView(dashboard, dashboardLp);

        LinearLayout batteryCard = column();
        batteryCard.setPadding(dp(20), dp(20), dp(20), dp(20));
        batteryCard.setBackground(shape(SURFACE, 22, 0xFF23343B));
        LinearLayout batteryTop = row();
        batteryTop.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout batteryLeft = column();
        batteryLeft.addView(text(getString(R.string.battery_level), 12, MUTED, false));
        batteryText = text("— %", 42, TEXT, true);
        batteryText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        batteryText.setPadding(0, dp(6), 0, 0);
        batteryLeft.addView(batteryText);
        batteryTop.addView(batteryLeft, new LinearLayout.LayoutParams(0, -2, 1));
        batteryCaption = text("", 12, MINT, false);
        batteryCaption.setVisibility(View.GONE);
        batteryCaption.setGravity(Gravity.CENTER);
        batteryCaption.setPadding(dp(12), dp(7), dp(12), dp(7));
        batteryCaption.setBackground(shape(0xFF203B32, 16, 0));
        batteryTop.addView(batteryCaption);
        batteryCard.addView(batteryTop);
        LinearLayout segments = row();
        for (int i = 0; i < batterySegments.length; i++) {
            View segment = new View(this);
            segment.setBackground(shape(0xFF263B3D, 3, 0));
            LinearLayout.LayoutParams segmentLp = new LinearLayout.LayoutParams(0, dp(5), 1);
            if (i > 0) segmentLp.leftMargin = dp(4);
            segments.addView(segment, segmentLp);
            batterySegments[i] = segment;
        }
        LinearLayout.LayoutParams segmentsLp = new LinearLayout.LayoutParams(-1, -2);
        segmentsLp.topMargin = dp(12);
        batteryCard.addView(segments, segmentsLp);

        View divider = new View(this);
        divider.setBackgroundColor(0xFF293B42);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.topMargin = dp(20);
        batteryCard.addView(divider, dividerLp);

        LinearLayout details = row();
        LinearLayout temp = metric(getString(R.string.battery_temperature));
        temperatureText = text("— °C", 20, TEXT, true);
        temp.addView(temperatureText);
        LinearLayout health = metric(getString(R.string.battery_health));
        healthText = text("—", 20, TEXT, false);
        health.addView(healthText);
        LinearLayout.LayoutParams metricLp = new LinearLayout.LayoutParams(0, -2, 1);
        metricLp.rightMargin = dp(6);
        details.addView(temp, metricLp);
        LinearLayout.LayoutParams healthLp = new LinearLayout.LayoutParams(0, -2, 1);
        healthLp.leftMargin = dp(6);
        details.addView(health, healthLp);
        add(batteryCard, details, 16);
        add(overview, batteryCard, 0);

        LinearLayout diagnosisCard = column();
        diagnosisCard.setPadding(dp(20), dp(16), dp(20), dp(12));
        diagnosisCard.setBackground(shape(SURFACE, 22, 0xFF23343B));
        TextView diagnosisTitle = text(getString(R.string.diagnosis_mode), 14, TEXT, true);
        if (android.os.Build.VERSION.SDK_INT >= 28) diagnosisTitle.setAccessibilityHeading(true);
        diagnosisCard.addView(diagnosisTitle);
        scanChoices = new RadioGroup(this);
        scanChoices.setOrientation(LinearLayout.VERTICAL);
        quickScanOption = scanOption(R.string.scan_quick, R.string.scan_quick_description);
        fullScanOption = scanOption(R.string.scan_full, R.string.scan_full_description);
        scanChoices.addView(quickScanOption, new RadioGroup.LayoutParams(-1, -2));
        scanChoices.addView(fullScanOption, new RadioGroup.LayoutParams(-1, -2));
        scanChoices.check(scanMode == OptimizationEngine.SCAN_FULL
                ? fullScanOption.getId() : quickScanOption.getId());
        scanChoices.setOnCheckedChangeListener((group, checkedId) -> {
            if (mode == OptimizationOrbView.RUNNING || closeRequest != null) return;
            int selected = checkedId == fullScanOption.getId()
                    ? OptimizationEngine.SCAN_FULL : OptimizationEngine.SCAN_QUICK;
            if (scanMode == selected) return;
            scanMode = selected;
            preferences.edit().putInt("scan_mode", scanMode).apply();
        });
        add(diagnosisCard, scanChoices, 8);
        add(overview, diagnosisCard, 20);

        orb = new OptimizationOrbView(this);
        orb.setRepeatEnabled(optimizationRequest != null);
        orb.setId(View.generateViewId());
        if (optimizationRequest != null) orb.setOnClickListener(v -> optimizationRequest.run());
        int orbSize = dp(landscape ? 220 : compactPortrait ? 216 : 280);
        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams(-1, orbSize);
        hero.addView(orb, orbLp);
        messageText = text("", 16, TEXT, true);
        messageText.setGravity(Gravity.CENTER);
        messageText.setPadding(dp(14), 0, dp(14), 0);
        messageText.setLineSpacing(dp(4), 1);
        messageText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        messageText.setVisibility(View.GONE);
        add(hero, messageText, 0);
        lastRunText = text("", 12, MUTED, false);
        lastRunText.setGravity(Gravity.CENTER);
        add(hero, lastRunText, 12);
        rootView = root;
        root.requestApplyInsets();
    }

    private LinearLayout metric(String label) {
        LinearLayout card = column();
        TextView caption = text(label, 12, MUTED, false);
        caption.setPadding(0, 0, 0, dp(6));
        card.addView(caption);
        return card;
    }

    private RadioButton scanOption(int titleRes, int descriptionRes) {
        String title = getString(titleRes);
        String description = getString(descriptionRes);
        SpannableString label = new SpannableString(title + "\n" + description);
        int descriptionStart = title.length() + 1;
        label.setSpan(new RelativeSizeSpan(12f / 15f), descriptionStart, label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(new ForegroundColorSpan(MUTED), descriptionStart, label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        RadioButton option = new RadioButton(this);
        option.setId(View.generateViewId());
        option.setText(label);
        option.setTextSize(15);
        option.setTextColor(TEXT);
        option.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        option.setIncludeFontPadding(false);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setLineSpacing(dp(3), 1);
        option.setMinHeight(dp(64));
        option.setPadding(dp(2), dp(8), dp(4), dp(8));
        option.setButtonTintList(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{MINT, MUTED}));
        option.setBackground(ripple(SURFACE, 12));
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
        option.setMinHeight(dp(64));
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
        panel.setBackground(shape(SURFACE, 24, 0xFF2B4548));
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
        input.setBackgroundTintList(ColorStateList.valueOf(MINT));
        input.setContentDescription(getString(R.string.duration_input));
        input.setText(String.valueOf(animationDuration / 1000));
        input.selectAll();
        add(panel, input, 12);
        add(panel, text(getString(R.string.master_clear_label), 14, TEXT, true), 24);
        RadioGroup choices = new RadioGroup(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        RadioButton one = masterClearOption(R.string.master_clear_one);
        RadioButton zero = masterClearOption(R.string.master_clear_zero);
        choices.addView(one, new RadioGroup.LayoutParams(-1, -2));
        choices.addView(zero, new RadioGroup.LayoutParams(-1, -2));
        choices.check(master_clear_selected == 0 ? zero.getId() : one.getId());
        add(panel, choices, 8);
        LinearLayout actions = row();
        Button cancel = new Button(this);
        cancel.setText(R.string.cancel_action);
        cancel.setTextColor(TEXT);
        cancel.setAllCaps(false);
        cancel.setBackground(ripple(0xFF23343B, 14));
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button save = new Button(this);
        save.setText(R.string.save_action);
        save.setTextColor(BG);
        save.setAllCaps(false);
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
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        saveLp.leftMargin = dp(12);
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
        // 起動時に準備したオーバーレイにも、直前の選択・秒数を反映します。
        loadOptions();
        progressSeed = ThreadLocalRandom.current().nextLong();
        progress = new OptimizationProgress(animationDuration, progressSeed);
        renderState();
        renderProgress(0);
    }

    /** WindowManager.addView() の直後に既存の最適化演出を開始します。 */
    void startPreparedOptimization() {
        startedAt = SystemClock.elapsedRealtime();
        startUpdates();
        orb.announceForAccessibility(getString(R.string.optimization_started));
    }

    private void renderState() {
        boolean running = mode == OptimizationOrbView.RUNNING;
        rootView.setKeepScreenOn(running);
        scanChoices.check(scanMode == OptimizationEngine.SCAN_FULL
                ? fullScanOption.getId() : quickScanOption.getId());
        boolean canChooseScan = !running && closeRequest == null;
        quickScanOption.setEnabled(canChooseScan);
        fullScanOption.setEnabled(canChooseScan);
        scanChoices.setAlpha(canChooseScan ? 1f : .65f);
        messageText.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) messageText.setText(R.string.keep_screen_open);
        orb.setState(mode, mode == OptimizationOrbView.COMPLETE ? 1 : 0);
        if (closeRequest != null && mode != OptimizationOrbView.RUNNING) {
            orb.setClickable(false);
            orb.setFocusable(false);
            orb.setContentDescription(getString(mode == OptimizationOrbView.COMPLETE ?
                    R.string.optimization_complete : R.string.optimize_action));
        }
        // 未実行時は代わりのコピーを置かず、日時があるときだけ表示します。
        lastRunText.setVisibility(lastRun == 0 || closeRequest != null ? View.GONE : View.VISIBLE);
        if (lastRun != 0) lastRunText.setText(getString(R.string.last_run,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.JAPAN).format(new Date(lastRun))));
    }

    private void renderProgress(float fraction) {
        orb.setState(OptimizationOrbView.RUNNING, fraction);
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
                    showMessage(getString(R.string.optimization_failed));
                });
            }
        });
    }

    private void updateBattery(Intent intent) {
        if (intent == null) return;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
        batteryText.setText(percent < 0 ? "— %" : percent + " %");
        batteryText.setContentDescription(percent < 0 ? "バッテリー残量を取得できません" : "バッテリー残量 " + percent + "パーセント");
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING;
        boolean full = status == BatteryManager.BATTERY_STATUS_FULL;
        batteryCaption.setVisibility(charging || full ? View.VISIBLE : View.GONE);
        if (charging || full) batteryCaption.setText(charging ? R.string.battery_charging : R.string.battery_full);
        for (int i = 0; i < batterySegments.length; i++) {
            boolean filled = percent >= 0 && i < Math.round(percent / 5f);
            batterySegments[i].setBackground(shape(filled ? (percent <= 20 ? 0xFFE9B873 : MINT) : 0xFF263B3D, 3, 0));
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
        healthText.setTextColor(health == BatteryManager.BATTERY_HEALTH_GOOD ? MINT : TEXT);
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
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3372E8BD),
                shape(color, radius, 0), shape(Color.WHITE, radius, 0));
    }
}
