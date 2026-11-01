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
    private Runnable overlayStateListener;

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

    boolean isOverlayShowing() { return attached; }

    void setOverlayStateListener(Runnable listener) {
        overlayStateListener = listener;
        notifyOverlayStateChanged();
    }

    private void notifyOverlayStateChanged() {
        if (overlayStateListener != null) overlayStateListener.run();
    }

    void prepareOverlay() {
        if (screen == null) {
            screen = new OptimizationScreen(windowContext, null, null, this::closeOverlayAndReturnToApp);
        }
        screen.prepareOptimization();
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
        notifyOverlayStateChanged();
    }

    private WindowManager.LayoutParams overlayParameters() {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (android.os.Build.VERSION.SDK_INT < 30) {
            // Android 10以前でも、内容側で安全領域を確保できるようにします。
            flags |= WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle(getString(R.string.app_name));
        params.windowAnimations = 0;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            // 背景はシステムバーの背後まで描き、内容の余白は画面側で確保します。
            params.setFitInsetsTypes(0);
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return params;
    }

    /** 閉じる操作では、既存のトップ画面を再利用して前面へ戻します。 */
    private void closeOverlayAndReturnToApp() {
        if (!attached) return;
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // ユーザー操作による画面復帰を、オーバーレイの表示中に要求します。
        startActivity(intent);
        hideOverlay();
    }

    void hideOverlay() {
        if (screen == null) return;
        if (attached) {
            windowManager.removeViewImmediate(screen.getView());
            attached = false;
        }
        screen.release();
        screen = null;
        notifyOverlayStateChanged();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (screen == null) return;
        Bundle state = new Bundle();
        screen.saveState(state);
        boolean wasAttached = attached;
        hideOverlay();
        screen = new OptimizationScreen(windowContext, state, null, this::closeOverlayAndReturnToApp);
        if (wasAttached) {
            windowManager.addView(screen.getView(), overlayParameters());
            attached = true;
            screen.startUpdates();
            notifyOverlayStateChanged();
        }
    }

    @Override public void onDestroy() {
        hideOverlay();
        super.onDestroy();
    }
}
