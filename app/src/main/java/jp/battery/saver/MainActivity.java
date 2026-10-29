package jp.battery.saver;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainActivity extends Activity {
    private static final int BG = 0xFF0B1419, SURFACE = 0xFF142128;
    private static final int TEXT = 0xFFF1F7F5, MUTED = 0xFF8EA6A4, MINT = 0xFF72E8BD;
    private static final long ANIMATION_MS = 7200;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> optimizationJob;
    private OptimizationOrbView orb;
    private TextView batteryText, batteryCaption, temperatureText, healthText;
    private TextView titleText, subtitleText, hintText, lastRunText, stepStatus;
    private final TextView[] stepIcons = new TextView[3];
    private final TextView[] stepLabels = new TextView[3];
    private final View[] batterySegments = new View[20];
    private int mode = OptimizationOrbView.READY, previousStage = -1;
    private long startedAt, lastRun, animationDuration = ANIMATION_MS;
    private boolean registered, foreground, destroyed, compactPortrait;
    private SharedPreferences preferences;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateBattery(intent); }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (destroyed || mode != OptimizationOrbView.RUNNING) return;
            float fraction = Math.min(1f, (SystemClock.elapsedRealtime() - startedAt) / (float) animationDuration);
            renderProgress(fraction);
            if (fraction >= 1) runOptimizationAfterAnimation();
            else if (foreground) main.postDelayed(this, 33);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("battery_saver", MODE_PRIVATE);
        lastRun = preferences.getLong("last_optimization", 0);
        if (state != null) {
            mode = state.getInt("mode", OptimizationOrbView.READY);
            startedAt = state.getLong("startedAt", 0);
            animationDuration = state.getLong("duration", ANIMATION_MS);
        }
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
        buildScreen();
        renderState();
    }

    private void buildScreen() {
        android.util.DisplayMetrics display = getResources().getDisplayMetrics();
        boolean landscape = display.widthPixels > display.heightPixels;
        compactPortrait = !landscape && display.heightPixels / display.density < 760;
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
        content.setPadding(dp(24), dp(landscape ? 8 : 18), dp(24), dp(landscape ? 8 : 16));
        outer.addView(content, new LinearLayout.LayoutParams(-1, -2));
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
        brand.setBackground(shape(0xFF18362C, 14, 0));
        brand.setPadding(dp(7), dp(7), dp(7), dp(7));
        brand.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(brand, lp(42, 42));
        LinearLayout brandText = column();
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, -2, 1);
        brandLp.leftMargin = dp(12);
        header.addView(brandText, brandLp);
        brandText.addView(text("Battery Saver", 20, TEXT, true));
        TextView brandSub = text("バッテリーを、もっと長く。", 10, MUTED, false);
        brandSub.setPadding(0, dp(3), 0, 0);
        brandText.addView(brandSub);
        TextView info = text("i", 19, MUTED, true);
        info.setGravity(Gravity.CENTER);
        info.setBackground(ripple(0xFF142128, 24));
        info.setContentDescription("バッテリーを長持ちさせるヒント");
        info.setFocusable(true);
        info.setOnClickListener(v -> showTips());
        header.addView(info, lp(48, 48));
        add(content, header, 0);

        // 横画面では2列、小さい縦画面では中央ボタンを上に配置します。
        LinearLayout overview = content;
        LinearLayout hero = content;
        if (landscape) {
            LinearLayout dashboard = row();
            overview = column();
            hero = column();
            LinearLayout.LayoutParams overviewLp = new LinearLayout.LayoutParams(0, -2, 1);
            overviewLp.rightMargin = dp(16);
            dashboard.addView(overview, overviewLp);
            dashboard.addView(hero, new LinearLayout.LayoutParams(0, -2, 1));
            add(content, dashboard, 12);
        }

        LinearLayout batteryCard = column();
        batteryCard.setPadding(dp(20), dp(16), dp(20), dp(16));
        batteryCard.setBackground(shape(SURFACE, 22, 0xFF23343B));
        LinearLayout batteryTop = row();
        batteryTop.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout batteryLeft = column();
        batteryLeft.addView(tracking("BATTERY LEVEL", 10, MUTED));
        batteryText = text("— %", 36, TEXT, true);
        batteryText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        batteryText.setPadding(0, dp(2), 0, 0);
        batteryLeft.addView(batteryText);
        batteryTop.addView(batteryLeft, new LinearLayout.LayoutParams(0, -2, 1));
        batteryCaption = text("情報を取得中", 11, MINT, false);
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
        if (!compactPortrait) add(overview, batteryCard, landscape ? 0 : 22);

        LinearLayout heading = column();
        heading.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView powerLabel = tracking("SMART POWER", 10, MINT);
        powerLabel.setGravity(Gravity.CENTER);
        heading.addView(powerLabel);
        titleText = text("ひと押しで、スマートな省電力。", landscape || compactPortrait ? 17 : 19, TEXT, true);
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, dp(10), 0, 0);
        heading.addView(titleText);
        subtitleText = text("毎日のバッテリーに、心地よいゆとりを。", 11, MUTED, false);
        subtitleText.setGravity(Gravity.CENTER);
        subtitleText.setPadding(0, dp(7), 0, 0);
        heading.addView(subtitleText);
        add(hero, heading, landscape ? 0 : compactPortrait ? 18 : 25);

        orb = new OptimizationOrbView(this);
        orb.setId(View.generateViewId());
        orb.setOnClickListener(v -> startOptimization());
        int orbSize = dp(landscape ? 170 : compactPortrait ? 230 : 280);
        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams(-1, orbSize);
        orbLp.topMargin = dp(6);
        hero.addView(orb, orbLp);
        hintText = text("タップひとつで、バッテリーを最適化", 11, MUTED, false);
        hintText.setGravity(Gravity.CENTER);
        hintText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(hero, hintText, 0);
        if (compactPortrait) add(overview, batteryCard, 20);

        LinearLayout steps = column();
        steps.setPadding(dp(18), dp(16), dp(18), dp(16));
        steps.setBackground(shape(SURFACE, 20, 0xFF23343B));
        LinearLayout stepsHeading = row();
        stepsHeading.addView(text("最適化のステップ", 12, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        stepStatus = tracking("3 STEPS", 9, MUTED);
        stepsHeading.addView(stepStatus);
        steps.addView(stepsHeading);
        LinearLayout stepRow = row();
        String[] labels = {"バッテリー確認", "電力チェック", "仕上げ"};
        for (int i = 0; i < 3; i++) {
            LinearLayout step = column();
            step.setGravity(Gravity.CENTER);
            stepIcons[i] = text("0" + (i + 1), 10, MUTED, true);
            stepIcons[i].setGravity(Gravity.CENTER);
            stepIcons[i].setBackground(shape(0xFF213139, 12, 0));
            step.addView(stepIcons[i], lp(26, 26));
            stepLabels[i] = text(labels[i], 10, MUTED, false);
            stepLabels[i].setGravity(Gravity.CENTER);
            stepLabels[i].setPadding(0, dp(9), 0, 0);
            step.addView(stepLabels[i]);
            stepRow.addView(step, new LinearLayout.LayoutParams(0, -2, 1));
        }
        LinearLayout.LayoutParams stepRowLp = new LinearLayout.LayoutParams(-1, -2);
        stepRowLp.topMargin = dp(15);
        steps.addView(stepRow, stepRowLp);
        add(overview, steps, landscape ? 12 : 24);

        LinearLayout details = row();
        LinearLayout temp = metric("バッテリー温度");
        temperatureText = text("— °C", 19, TEXT, true);
        temp.addView(temperatureText);
        LinearLayout health = metric("バッテリーの状態");
        healthText = text("—", 19, TEXT, true);
        health.addView(healthText);
        LinearLayout.LayoutParams metricLp = new LinearLayout.LayoutParams(0, -2, 1);
        metricLp.rightMargin = dp(6);
        details.addView(temp, metricLp);
        LinearLayout.LayoutParams healthLp = new LinearLayout.LayoutParams(0, -2, 1);
        healthLp.leftMargin = dp(6);
        details.addView(health, healthLp);
        add(overview, details, 12);
        lastRunText = text("", 10, MUTED, false);
        lastRunText.setGravity(Gravity.CENTER);
        add(overview, lastRunText, 18);
        setContentView(root);
        root.requestApplyInsets();
    }

    private LinearLayout metric(String label) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(13), dp(12), dp(13));
        card.setBackground(shape(SURFACE, 18, 0xFF23343B));
        TextView caption = text(label, 10, MUTED, false);
        caption.setPadding(0, 0, 0, dp(6));
        card.addView(caption);
        return card;
    }

    private void startOptimization() {
        if (mode == OptimizationOrbView.RUNNING) return;
        orb.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30 ?
                HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
        mode = OptimizationOrbView.RUNNING;
        startedAt = SystemClock.elapsedRealtime();
        animationDuration = ValueAnimator.areAnimatorsEnabled() ? ANIMATION_MS : 600;
        previousStage = -1;
        renderState();
        orb.announceForAccessibility("最適化を開始しました");
        main.removeCallbacks(ticker);
        main.post(ticker);
    }

    private void renderState() {
        if (mode == OptimizationOrbView.RUNNING) {
            titleText.setText("バッテリーを最適化しています");
            subtitleText.setText("このまま、少しだけお待ちください。");
            orb.setState(mode, 0);
        } else if (mode == OptimizationOrbView.COMPLETE) {
            titleText.setText("最適化が完了しました");
            subtitleText.setText("すっきり整えて、次のひとときへ。");
            hintText.setText("いつでも、ここから再び最適化できます");
            stepStatus.setText(R.string.steps_complete);
            orb.setState(mode, 1);
            updateSteps(3);
        } else {
            titleText.setText(compactPortrait ? "ひと押しで、スマートに。" : "ひと押しで、スマートな省電力。");
            subtitleText.setText("毎日のバッテリーに、心地よいゆとりを。");
            hintText.setText("タップひとつで、バッテリーを最適化");
            stepStatus.setText(R.string.steps_ready);
            orb.setState(mode, 0);
            updateSteps(-1);
        }
        lastRunText.setText(lastRun == 0 ? "POWER CARE, EVERY DAY" : "前回の最適化  " +
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.JAPAN).format(new Date(lastRun)));
    }

    private void renderProgress(float fraction) {
        orb.setState(OptimizationOrbView.RUNNING, fraction);
        int stage = Math.min(2, (int)(fraction * 3));
        if (stage != previousStage) {
            previousStage = stage;
            updateSteps(stage);
            String[] messages = {"バッテリー情報を確認しています…", "電力の使用状況をチェックしています…", "最適化を仕上げています…"};
            hintText.setText(messages[stage]);
            stepStatus.setText(getString(R.string.steps_progress, stage + 1));
        }
    }

    private void updateSteps(int active) {
        for (int i = 0; i < 3; i++) {
            boolean done = active > i, current = active == i;
            stepIcons[i].setText(done ? "✓" : "0" + (i + 1));
            stepIcons[i].setTextColor(done || current ? MINT : MUTED);
            stepIcons[i].setBackground(shape(done || current ? 0xFF234438 : 0xFF213139, 12, current ? 0xFF4C9C7E : 0));
            stepLabels[i].setTextColor(done || current ? TEXT : MUTED);
        }
    }

    /** 演出の終点。実処理は OptimizationEngine.performOptimization() に追加してください。 */
    private void runOptimizationAfterAnimation() {
        if (optimizationJob != null) return;
        optimizationJob = executor.submit(() -> {
            try {
                // 【接続箇所】演出100% → 実処理 → 正常終了 → 完了画面 の順序です。
                OptimizationEngine.performOptimization(getApplicationContext());
                main.post(() -> {
                    if (destroyed) return;
                    optimizationJob = null;
                    mode = OptimizationOrbView.COMPLETE;
                    lastRun = System.currentTimeMillis();
                    preferences.edit().putLong("last_optimization", lastRun).apply();
                    renderState();
                    orb.performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30 ?
                HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
                    orb.announceForAccessibility("最適化が完了しました");
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (destroyed) return;
                    optimizationJob = null;
                    mode = OptimizationOrbView.READY;
                    renderState();
                    hintText.setText("完了できませんでした。もう一度お試しください。");
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
        String caption = status == BatteryManager.BATTERY_STATUS_CHARGING ? "●  充電中" :
                status == BatteryManager.BATTERY_STATUS_FULL ? "●  充電完了" :
                status == BatteryManager.BATTERY_STATUS_UNKNOWN ? "状態を取得中" : "●  バッテリー使用中";
        batteryCaption.setText(caption);
        for (int i = 0; i < batterySegments.length; i++) {
            boolean filled = percent >= 0 && i < Math.round(percent / 5f);
            batterySegments[i].setBackground(shape(filled ? (percent <= 20 ? 0xFFE9B873 : MINT) : 0xFF263B3D, 3, 0));
        }
        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        temperatureText.setText(temperature == Integer.MIN_VALUE ? "— °C" : String.format(Locale.JAPAN, "%.1f °C", temperature / 10f));
        int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
        String healthLabel;
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: healthLabel = "良好"; break;
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

    private void showTips() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = column();
        panel.setPadding(dp(26), dp(28), dp(26), dp(24));
        panel.setBackground(shape(SURFACE, 26, 0xFF2B4548));
        panel.addView(tracking("BATTERY CARE", 10, MINT));
        TextView heading = text("長持ちのための、小さな習慣。", 19, TEXT, true);
        add(panel, heading, 14);
        String[] titles = {"画面の明るさを心地よく", "使わない通信はオフに", "熱のこもらない場所へ"};
        String[] descriptions = {"明るさの自動調整や、短めの画面消灯時間を活用しましょう。", "使っていないときの位置情報やテザリング設定を見直しましょう。", "直射日光や高温の場所を避けて、バッテリーをいたわりましょう。"};
        for (int i = 0; i < titles.length; i++) {
            add(panel, text(titles[i], 14, MINT, true), 24);
            TextView description = text(descriptions[i], 12, MUTED, false);
            description.setLineSpacing(dp(4), 1);
            add(panel, description, 7);
        }
        Button close = new Button(this);
        close.setText("閉じる");
        close.setTextSize(14);
        close.setTextColor(BG);
        close.setAllCaps(false);
        close.setBackground(ripple(MINT, 15));
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, dp(48));
        closeLp.topMargin = dp(28);
        panel.addView(close, closeLp);
        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.setFillViewport(true);
        dialogScroll.setBackground(shape(SURFACE, 26, 0));
        dialogScroll.addView(panel);
        dialog.setContentView(dialogScroll);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.65f);
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(400)), -2);
        }
        dialog.show();
        if (window != null) window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(400)), -2);
    }

    @Override protected void onStart() {
        super.onStart();
        foreground = true;
        orb.setForeground(true);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        } else registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registered = true;
        if (mode == OptimizationOrbView.RUNNING) main.post(ticker);
    }

    @Override protected void onStop() {
        foreground = false;
        orb.setForeground(false);
        main.removeCallbacks(ticker);
        if (registered) { unregisterReceiver(batteryReceiver); registered = false; }
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("mode", mode);
        out.putLong("startedAt", startedAt);
        out.putLong("duration", animationDuration);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        if (optimizationJob != null) optimizationJob.cancel(true);
        executor.shutdownNow();
        super.onDestroy();
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
    private TextView tracking(String value, int size, int color) {
        TextView view = text(value, size, color, true);
        view.setLetterSpacing(.16f);
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