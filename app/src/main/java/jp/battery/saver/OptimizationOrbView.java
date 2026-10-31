package jp.battery.saver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** 実行ボタン、進捗リング、完了チェックを一つの描画面で管理。 */
public final class OptimizationOrbView extends View {
    public static final int READY = 0, RUNNING = 1, COMPLETE = 2;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path path = new Path();
    private final int mint = Color.rgb(114, 232, 189);
    private final Shader halo = new RadialGradient(0, 0, 149,
            new int[]{0x2865E3B0, 0x1265E3B0, 0x0065E3B0}, new float[]{0, .62f, 1}, Shader.TileMode.CLAMP);
    private final Shader ring = new SweepGradient(0, 0, new int[]{0xFF3FAE8A, mint, 0xFFBBFFE0}, null);
    private final Shader face = new LinearGradient(-60, -90, 65, 100,
            new int[]{0xFF295F4E, 0xFF183F35, 0xFF102C26}, null, Shader.TileMode.CLAMP);
    private int mode = READY;
    private float progress;
    private boolean pressed;
    private boolean foreground;
    private boolean repeatEnabled = true;
    private long completeAt;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    public OptimizationOrbView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(context.getString(R.string.optimize_action));
    }

    public void setRepeatEnabled(boolean value) {
        repeatEnabled = value;
    }

    public void setState(int next, float value) {
        boolean changed = mode != next;
        if (changed && next == COMPLETE) completeAt = SystemClock.uptimeMillis();
        mode = next;
        progress = Math.max(0, Math.min(1, value));
        setClickable(mode == READY || (mode == COMPLETE && repeatEnabled));
        setFocusable(mode != RUNNING);
        setContentDescription(getContext().getString(mode == RUNNING ? R.string.optimization_progress :
                mode == COMPLETE ? (repeatEnabled ? R.string.optimize_again : R.string.optimization_complete) :
                        R.string.optimize_action));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            setStateDescription(mode == RUNNING ? progressPercent() + "%" :
                    null);
        }
        invalidate();
    }

    public void setForeground(boolean value) {
        foreground = value;
        if (value) invalidate();
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(mode == RUNNING ? "android.widget.ProgressBar" :
                isClickable() ? "android.widget.Button" : "android.view.View");
        if (mode == RUNNING) {
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                    AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, 100, progress * 100));
        }
    }

    @Override public boolean performClick() {
        return isClickable() && super.performClick();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isClickable()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float dx = event.getX() - getWidth() / 2f;
            float dy = event.getY() - getHeight() / 2f;
            if (Math.hypot(dx, dy) > Math.min(getWidth(), getHeight()) * .39f) return false;
            pressed = true;
            setPressed(true);
            invalidate();
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                   event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            boolean clicked = pressed && event.getActionMasked() == MotionEvent.ACTION_UP &&
                    Math.hypot(event.getX() - getWidth() / 2f, event.getY() - getHeight() / 2f) <=
                            Math.min(getWidth(), getHeight()) * .39f;
            pressed = false;
            setPressed(false);
            invalidate();
            if (clicked) performClick();
        }
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float unit = size / 300f;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float t = SystemClock.uptimeMillis() / 1000f;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(unit * (pressed ? .97f : 1f), unit * (pressed ? .97f : 1f));

        paint.setShader(halo);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(0, 0, 149, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(mode == READY ? 2 : 4);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFF203830);
        bounds.set(-124, -124, 124, 124);
        canvas.drawCircle(0, 0, 124, paint);
        if (mode != READY) {
            paint.setShader(ring);
            canvas.save();
            canvas.rotate(-90);
            canvas.drawArc(bounds, 0, mode == COMPLETE ? 360 : 360 * progress, false, paint);
            canvas.restore();
            paint.setShader(null);
        }
        if (mode == RUNNING) {
            float angle = (t * 88) % 360;
            paint.setColor(0x5072E8BD);
            paint.setStrokeWidth(1.5f);
            bounds.set(-132, -132, 132, 132);
            canvas.drawArc(bounds, angle, 56, false, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(face);
        paint.setShadowLayer(18, 0, 8, 0x44000000);
        canvas.drawCircle(0, 0, 103, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(0x553F9C7A);
        canvas.drawCircle(0, 0, 102.5f, paint);
        paint.setStyle(Paint.Style.FILL);

        if (mode == COMPLETE) {
            float reveal = Math.min(1, (SystemClock.uptimeMillis() - completeAt) / 420f);
            paint.setColor(0x2072E8BD);
            canvas.drawCircle(0, -20, 28, paint);
            canvas.save();
            canvas.translate(0, -20);
            canvas.scale(.6f + .4f * reveal, .6f + .4f * reveal);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            paint.setColor(mint);
            path.reset(); path.moveTo(-12, 0); path.lineTo(-3, 9); path.lineTo(14, -10);
            canvas.drawPath(path, paint);
            canvas.restore();
            drawText(canvas, getContext().getString(repeatEnabled ? R.string.optimize_again : R.string.complete_label),
                    0, 43, 20, 0xFFE7FFF4, medium);
        } else if (mode == RUNNING) {
            drawText(canvas, progressPercent() + "%", 0, 18, 44, 0xFFE7FFF4,
                    Typeface.create("sans-serif-light", Typeface.NORMAL));
        } else {
            canvas.save();
            canvas.translate(0, -26);
            paint.setColor(mint);
            path.reset(); path.moveTo(4, -24); path.lineTo(-17, 4); path.lineTo(-2, 4);
            path.lineTo(-6, 24); path.lineTo(18, -6); path.lineTo(3, -6); path.close();
            canvas.drawPath(path, paint);
            canvas.restore();
            drawText(canvas, getContext().getString(R.string.optimize_action), 0, 42, 20, 0xFFE7FFF4, medium);
        }
        canvas.restore();
        if (foreground && getWindowVisibility() == VISIBLE && isShown()) {
            if (mode == RUNNING || (mode == COMPLETE && SystemClock.uptimeMillis() - completeAt < 450)) {
                postInvalidateDelayed(33);
            }
        }
    }

    private int progressPercent() {
        return progress >= 1f ? 100 : Math.min(99, (int) (progress * 100));
    }

    private void drawText(Canvas canvas, String text, float x, float y, float size, int color, Typeface face) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size * getResources().getConfiguration().fontScale);
        paint.setTypeface(face);
        paint.setTextAlign(Paint.Align.CENTER);
        float width = paint.measureText(text);
        if (width > 180) paint.setTextSize(paint.getTextSize() * 180 / width);
        canvas.drawText(text, x, y, paint);
    }
}
