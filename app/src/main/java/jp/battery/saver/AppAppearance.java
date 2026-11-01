package jp.battery.saver;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** 表示モードは端末の設定と独立して保存し、通常画面とオーバーレイで共有します。 */
final class AppAppearance {
    static final String DARK_MODE = "dark_mode";
    private static final Palette LIGHT = new Palette(false,
            0xFFF4F6F3, 0xFFFFFFFF, 0xFF17251E, 0xFF637168,
            0xFF246B4A, 0xFFFFFFFF, 0xFFDDE4DC, 0xFFE6F0E8,
            0xFFD4E7DA, 0xFF99531D, 0xFFA56724, 0x22246B4A);
    private static final Palette DARK = new Palette(true,
            0xFF101813, 0xFF1B261F, 0xFFEDF5EF, 0xFFACBDB0,
            0xFF7EDBA3, 0xFF10251A, 0xFF34463A, 0xFF243D2E,
            0xFF35543E, 0xFFE9B477, 0xFFE9B477, 0x337EDBA3);

    private AppAppearance() { }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("battery_saver", Context.MODE_PRIVATE);
    }

    static Palette palette(Context context) {
        return preferences(context).getBoolean(DARK_MODE, false) ? DARK : LIGHT;
    }

    static int theme(Context context) {
        return palette(context).dark ? R.style.AppTheme_Dark : R.style.AppTheme;
    }

    static int systemBarBackground(Context context) {
        boolean systemDark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return systemDark ? DARK.background : LIGHT.background;
    }

    static void toggle(Context context) {
        preferences(context).edit().putBoolean(DARK_MODE, !palette(context).dark).apply();
    }

    static final class Palette {
        final boolean dark;
        final int background, surface, text, muted, accent, onAccent;
        final int border, softGreen, pressedGreen, warning, lowBattery, ripple;

        Palette(boolean dark, int background, int surface, int text, int muted,
                int accent, int onAccent, int border, int softGreen, int pressedGreen,
                int warning, int lowBattery, int ripple) {
            this.dark = dark;
            this.background = background;
            this.surface = surface;
            this.text = text;
            this.muted = muted;
            this.accent = accent;
            this.onAccent = onAccent;
            this.border = border;
            this.softGreen = softGreen;
            this.pressedGreen = pressedGreen;
            this.warning = warning;
            this.lowBattery = lowBattery;
            this.ripple = ripple;
        }
    }
}
