package jp.battery.saver;

import android.content.Context;

/** 最適化の実処理をまとめる入口。画面・アニメーションから独立しています。 */
public final class OptimizationEngine {
    public static final int SCAN_QUICK = 0;
    public static final int SCAN_FULL = 1;

    private OptimizationEngine() { }

    /** 既存の呼び出しはクイックスキャンとして扱います。 */
    public static void performOptimization(Context context, int master_clear) throws Exception {
        performOptimization(context, master_clear, SCAN_QUICK);
    }

    /**
     * 【実際の節約処理を追加する場所】
     * MainActivity のアニメーションが100%に到達すると、このメソッドが呼ばれます。
     * このメソッドが正常に終了した後に、画面が「最適化完了」へ切り替わります。
     *
     * ・バックグラウンドスレッドで実行されます。ここで View を操作しないでください。
     * ・context は Application Context です。Activity を保持しないでください。
     * ・master_clear は設定で選んだ 0 または 1 です（初期値 1）。
     *   OptimizationScreen.master_clear_selected の値を開始時に読み込み、ここへ渡します。
     * ・scanMode は診断モードカードの選択値です。SCAN_QUICK / SCAN_FULL を使って分岐できます。
     *   演出開始時に値を固定し、実行途中の変更は受け付けません。
     * ・必要な実処理を下の TODO の位置に追加してください。
     * ・失敗した場合は例外を投げてください。画面側が再試行できる状態に戻します。
     * ・画面終了時は割り込みが入ります。長い処理では割り込みを確認してください。
     * ・権限が必要な機能は、MainActivity 側でユーザーの許可を得てから呼び出します。
     *   Androidで許可されているAPIのみを使用し、設定画面の起動が必要な操作は
     *   Activity側に実装してください。
     */
    public static void performOptimization(Context context, int master_clear, int scanMode) throws Exception {
        if (scanMode != SCAN_QUICK && scanMode != SCAN_FULL) {
            throw new IllegalArgumentException("Unknown scan mode: " + scanMode);
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Optimization cancelled");
        }

        // TODO: ここに、実際のバッテリー節約・最適化処理を実装してください。
        // scanMode == SCAN_QUICK: 主な診断項目のみ、SCAN_FULL: 全診断項目を実行する分岐を追加します。
        // 現時点ではモード別の診断処理は未実装です。
        // 例: アプリ自身の不要な定期処理の停止、同期頻度の調整、計測結果の保存など。
        // 正常に return すると完了画面へ進みます。追加の待機処理は不要です。
    }
}
