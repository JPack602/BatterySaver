package jp.battery.saver;

import java.util.Random;

/** 実行ごとの乱数から、単調増加して指定時間ちょうどに1になる進捗を生成します。 */
final class OptimizationProgress {
    private static final int SEGMENTS = 24;
    private final long durationMillis;
    private final float[] checkpoints = new float[SEGMENTS + 1];

    OptimizationProgress(long durationMillis, long seed) {
        if (durationMillis <= 0) throw new IllegalArgumentException("Duration must be positive");
        this.durationMillis = durationMillis;
        Random random = new Random(seed);
        for (int i = 1; i <= SEGMENTS; i++) {
            // 小さな増分と大きな増分を混ぜ、区間ごとに速さを変えます。
            float speed = random.nextFloat();
            checkpoints[i] = checkpoints[i - 1] + .05f + speed * speed * 3f;
        }
        float total = checkpoints[SEGMENTS];
        for (int i = 1; i < SEGMENTS; i++) checkpoints[i] /= total;
        checkpoints[SEGMENTS] = 1f;
    }

    float at(long elapsedMillis) {
        if (elapsedMillis <= 0) return 0f;
        if (elapsedMillis >= durationMillis) return 1f;
        double position = elapsedMillis / (double) durationMillis * SEGMENTS;
        int segment = Math.min(SEGMENTS - 1, (int) position);
        float local = (float) (position - segment);
        float eased = local * local * (3f - 2f * local);
        // 最終フレームの前に計算誤差で100%へ到達することを防ぎます。
        return Math.min(Math.nextDown(1f), checkpoints[segment] +
                (checkpoints[segment + 1] - checkpoints[segment]) * eased);
    }
}
