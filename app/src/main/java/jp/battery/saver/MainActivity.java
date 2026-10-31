package jp.battery.saver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int OVERLAY_PERMISSION_REQUEST = 100;
    private static final String OVERLAY_CONSENT_SHOWN = "overlay_consent_shown";
    private OptimizationScreen screen;
    private AlertDialog overlayConsentDialog;
    private String messageAfterResume;
    private OptimizationOverlayService overlayService;
    private boolean bound, resumed, launchPending, waitingForPermission, settingsLaunched;
    private boolean overlayConsentPending, optimizeAfterPermission;

    private final ServiceConnection overlayConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            overlayService = ((OptimizationOverlayService.LocalBinder) binder).getService();
            launchWhenReady();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            overlayService = null;
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            waitingForPermission = state.getBoolean("waitingForPermission");
            overlayConsentPending = state.getBoolean("overlayConsentPending");
            optimizeAfterPermission = state.getBoolean("optimizeAfterPermission");
        } else {
            overlayConsentPending = !Settings.canDrawOverlays(this)
                    && !getPreferences(MODE_PRIVATE).getBoolean(OVERLAY_CONSENT_SHOWN, false);
        }
        configureWindow();
        screen = new OptimizationScreen(this, null, this::requestOptimization, null);
        setContentView(screen.getView());
        // Service と View の生成を設定起動前に済ませ、測定対象の区間から除きます。
        bound = bindService(new Intent(this, OptimizationOverlayService.class),
                overlayConnection, Context.BIND_AUTO_CREATE);
        if (!bound) screen.showMessage(getString(R.string.preparation_failed));
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(0xFF0B1419);
        window.setNavigationBarColor(0xFF0B1419);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
    }

    private void requestOptimization() {
        if (launchPending || waitingForPermission || settingsLaunched || overlayConsentPending) return;
        if (!Settings.canDrawOverlays(this)) {
            optimizeAfterPermission = true;
            overlayConsentPending = true;
            showOverlayConsent();
            return;
        }
        launchPending = true;
        launchWhenReady();
    }

    private void showOverlayConsent() {
        if (!resumed || !overlayConsentPending || overlayConsentDialog != null) return;
        if (Settings.canDrawOverlays(this)) {
            overlayConsentPending = false;
            launchPending = optimizeAfterPermission;
            optimizeAfterPermission = false;
            launchWhenReady();
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean(OVERLAY_CONSENT_SHOWN, true).apply();
        overlayConsentDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.overlay_consent_title)
                .setMessage(R.string.overlay_consent_message)
                .setPositiveButton(R.string.overlay_consent_accept, (dialog, which) -> {
                    overlayConsentPending = false;
                    openOverlayPermissionSettings();
                })
                .setNegativeButton(R.string.overlay_consent_later, (dialog, which) -> deferOverlayPermission())
                .setOnCancelListener(dialog -> deferOverlayPermission())
                .create();
        overlayConsentDialog.setCanceledOnTouchOutside(false);
        overlayConsentDialog.setOnDismissListener(dialog -> overlayConsentDialog = null);
        overlayConsentDialog.show();
    }

    private void deferOverlayPermission() {
        overlayConsentPending = false;
        optimizeAfterPermission = false;
    }

    /** この設定画面は、説明ダイアログで明示的に同意した場合だけ開きます。 */
    private void openOverlayPermissionSettings() {
        waitingForPermission = true;
        try {
            Toast.makeText(this, getString(R.string.overlay_permission_prompt, getString(R.string.app_name)),
                    Toast.LENGTH_LONG).show();
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), OVERLAY_PERMISSION_REQUEST);
        } catch (ActivityNotFoundException | SecurityException error) {
            waitingForPermission = false;
            optimizeAfterPermission = false;
            screen.showMessage(getString(R.string.overlay_permission_required));
        }
    }

    private void launchWhenReady() {
        if (!resumed || !launchPending || overlayService == null) return;
        launchPending = false;
        if (!Settings.canDrawOverlays(this)) {
            screen.showMessage(getString(R.string.overlay_permission_required));
            return;
        }
        try {
            overlayService.prepareOverlay();
            settingsLaunched = true;

            // 【起動箇所】android.settings.SETTINGS を起動します。
            // startActivity の復帰は相手 Activity の初回描画完了を意味しません。
            // ここでActivity起動するためのコードを書く
            startActivity(new Intent(Settings.ACTION_SETTINGS));

            // 選択した master_clear を出力
            // Toast.makeText(OptimizationScreen.master_clear_selected).show();

            // 【表示箇所】遅延・post・Service 起動待ちを挟まず、直後に addView します。
            overlayService.showPreparedOverlay();
        } catch (ActivityNotFoundException | IllegalStateException | SecurityException |
                 WindowManager.BadTokenException | WindowManager.InvalidDisplayException error) {
            settingsLaunched = false;
            overlayService.hideOverlay();
            screen.showMessage(getString(R.string.overlay_failed));
            Toast.makeText(this, R.string.overlay_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != OVERLAY_PERMISSION_REQUEST) return;
        waitingForPermission = false;
        boolean granted = Settings.canDrawOverlays(this);
        launchPending = granted && optimizeAfterPermission;
        optimizeAfterPermission = false;
        if (!granted) messageAfterResume = getString(R.string.overlay_permission_required);
    }

    @Override protected void onStart() {
        super.onStart();
        screen.startUpdates();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        settingsLaunched = false;
        if (overlayService != null) {
            overlayService.hideOverlay();
            overlayService.prepareOverlay();
        }
        screen.refreshLastRun();
        if (messageAfterResume != null) {
            screen.showMessage(messageAfterResume);
            messageAfterResume = null;
        }
        showOverlayConsent();
        launchWhenReady();
    }

    @Override protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override protected void onStop() {
        screen.stopUpdates();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("waitingForPermission", waitingForPermission);
        out.putBoolean("overlayConsentPending", overlayConsentPending);
        out.putBoolean("optimizeAfterPermission", optimizeAfterPermission);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        if (overlayConsentDialog != null) overlayConsentDialog.dismiss();
        screen.release();
        if (bound) {
            unbindService(overlayConnection);
            bound = false;
        }
        overlayService = null;
        super.onDestroy();
    }
}
