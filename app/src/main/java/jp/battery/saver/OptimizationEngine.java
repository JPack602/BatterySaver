package jp.battery.saver;

import android.content.Context;

/** 最適化の実処理をまとめる入口。画面・アニメーションから独立しています。 */
public final class OptimizationEngine {
    private OptimizationEngine() { }

    /**
     * 【実際の節約処理を追加する場所】
     * MainActivity のアニメーションが100%に到達すると、このメソッドが呼ばれます。
     * このメソッドが正常に終了した後に、画面が「最適化完了」へ切り替わります。
     *
     * ・バックグラウンドスレッドで実行されます。ここで View を操作しないでください。
     * ・context は Application Context です。Activity を保持しないでください。
     * ・必要な実処理を下の TODO の位置に追加してください。
     * ・失敗した場合は例外を投げてください。画面側が再試行できる状態に戻します。
     * ・画面終了時は割り込みが入ります。長い処理では割り込みを確認してください。
     * ・権限が必要な機能は、MainActivity 側でユーザーの許可を得てから呼び出します。
     *   Androidで許可されているAPIのみを使用し、設定画面の起動が必要な操作は
     *   Activity側に実装してください。
     */
    public static void performOptimization(Context context) throws Exception {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Optimization cancelled");
        }

        // TODO: ここに、実際のバッテリー節約・最適化処理を実装してください。
        // 例: アプリ自身の不要な定期処理の停止、同期頻度の調整、計測結果の保存など。
        // 正常に return すると完了画面へ進みます。追加の待機処理は不要です。
    }
}