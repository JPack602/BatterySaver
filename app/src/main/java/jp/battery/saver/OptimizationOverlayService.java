package jp.battery.saver;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.WindowInsets;

/** 同じプロセスに事前接続し、Activity が停止しても最適化画面を表示し続けます。 */
public final class OptimizationOverlayService extends Service {
    private final LocalBinder binder = new LocalBinder();
    private Context windowContext;
    private WindowManager windowManager;
    private OptimizationScreen screen;
    private boolean attached;

    final class LocalBinder extends Binder {
        OptimizationOverlayService getService() { return OptimizationOverlayService.this; }
    }

    @Override public void onCreate() {
        super.onCreate();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            Display display = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            windowContext = createDisplayContext(display).createWindowContext(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        } else {
            windowContext = this;
        }
        windowManager = windowContext.getSystemService(WindowManager.class);
        prepareOverlay();
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    void prepareOverlay() {
        if (screen == null) {
            screen = new OptimizationScreen(windowContext, null, null, this::hideOverlay);
            screen.prepareOptimization();
        }
    }

    void showPreparedOverlay() {
        if (attached) return;
        if (!Settings.canDrawOverlays(this)) {
            throw new SecurityException("Overlay permission is required");
        }
        if (screen == null) throw new IllegalStateException("Overlay must be prepared before launch");
        // 【オーバーレイ追加箇所】追加要求と、実際の初回描画は別の時点です。
        windowManager.addView(screen.getView(), overlayParameters());
        attached = true;
        screen.startPreparedOptimization();
    }

    private WindowManager.LayoutParams overlayParameters() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle(getString(R.string.app_name));
        params.windowAnimations = 0;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            params.setFitInsetsTypes(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return params;
    }

    void hideOverlay() {
        if (screen == null) return;
        if (attached) {
            windowManager.removeViewImmediate(screen.getView());
            attached = false;
        }
        screen.release();
        screen = null;
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (screen == null) return;
        Bundle state = new Bundle();
        screen.saveState(state);
        boolean wasAttached = attached;
        hideOverlay();
        screen = new OptimizationScreen(windowContext, state, null, this::hideOverlay);
        if (wasAttached) {
            windowManager.addView(screen.getView(), overlayParameters());
            attached = true;
            screen.startUpdates();
        }
    }

    @Override public void onDestroy() {
        hideOverlay();
        super.onDestroy();
    }
}
