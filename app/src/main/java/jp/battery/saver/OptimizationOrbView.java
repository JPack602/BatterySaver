package jp.battery.saver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** スキャンの進捗と完了状態を、同じ位置に表示します。 */
public final class OptimizationOrbView extends View {
    public static final int READY = 0, RUNNING = 1, COMPLETE = 2;
    private final AppAppearance.Palette colors;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF(-112, -112, 112, 112);
    private final Path check = new Path();
    private final Drawable optimizationBolt;
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private int mode = READY;
    private float progress;
    private boolean pressed;
    private boolean repeatEnabled = true;

    public OptimizationOrbView(Context context) {
        super(context);
        colors = AppAppearance.palette(context);
        optimizationBolt = context.getDrawable(R.drawable.ic_optimization_bolt);
        optimizationBolt.setTint(colors.accent);
        optimizationBolt.setBounds(-22, -22, 22, 22);
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(context.getString(R.string.optimize_action));
    }

    public void setRepeatEnabled(boolean value) {
        repeatEnabled = value;
        setState(mode, progress);
    }

    public void setState(int next, float value) {
        mode = next;
        progress = Math.max(0, Math.min(1, value));
        setClickable(mode == READY || (mode == COMPLETE && repeatEnabled));
        setFocusable(mode != RUNNING);
        setContentDescription(getContext().getString(mode == RUNNING ? R.string.optimization_progress :
                mode == COMPLETE ? (repeatEnabled ? R.string.optimize_again : R.string.optimization_complete) :
                        R.string.optimize_action));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            setStateDescription(mode == RUNNING ? progressPercent() + "%" : null);
        }
        invalidate();
    }

    public void setForeground(boolean value) {
        if (value) invalidate();
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(mode == RUNNING ? "android.widget.ProgressBar" :
                isClickable() ? "android.widget.Button" : "android.view.View");
        if (mode == RUNNING) {
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                    AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, 100, progressPercent()));
        }
    }

    @Override public boolean performClick() {
        return isClickable() && super.performClick();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isClickable()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (!insideCircle(event)) return false;
            pressed = true;
            setPressed(true);
            invalidate();
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                   event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            boolean clicked = pressed && event.getActionMasked() == MotionEvent.ACTION_UP && insideCircle(event);
            pressed = false;
            setPressed(false);
            invalidate();
            if (clicked) performClick();
        }
        return true;
    }

    private boolean insideCircle(MotionEvent event) {
        return Math.hypot(event.getX() - getWidth() / 2f, event.getY() - getHeight() / 2f) <=
                Math.min(getWidth(), getHeight()) * .42f;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float unit = Math.min(getWidth(), getHeight()) / 300f;
        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.scale(unit, unit);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        if (mode == RUNNING) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(9);
            paint.setColor(colors.softGreen);
            canvas.drawOval(bounds, paint);
            if (progress > 0) {
                paint.setColor(colors.accent);
                canvas.drawArc(bounds, -90, 360 * progress, false, paint);
            }
            drawText(canvas, progressPercent() + "%", 0, 48, colors.text, regular, 174, 76);
        } else if (mode == COMPLETE) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(colors.softGreen);
            canvas.drawCircle(0, -18, 47, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(colors.accent);
            check.reset();
            check.moveTo(-18, -19);
            check.lineTo(-5, -6);
            check.lineTo(20, -32);
            canvas.drawPath(check, paint);
            drawText(canvas, getContext().getString(repeatEnabled ? R.string.optimize_again : R.string.complete_label),
                    57, 21, colors.text, medium, 188, 32);
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed ? colors.pressedGreen : colors.softGreen);
            canvas.drawCircle(0, 0, 112, paint);
            canvas.save();
            canvas.translate(0, -26);
            optimizationBolt.draw(canvas);
            canvas.restore();
            drawText(canvas, getContext().getString(R.string.optimize_action),
                    36, 21, colors.accent, medium, 180, 36);
        }
        canvas.restore();
    }

    private int progressPercent() {
        return progress >= 1f ? 100 : Math.min(99, (int) (progress * 100));
    }

    /** 文字拡大を反映し、リング内に収まる範囲で表示します。 */
    private void drawText(Canvas canvas, String text, float centerY, float size, int color,
                          Typeface typeface, float maxWidth, float maxHeight) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTypeface(typeface);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size * getResources().getConfiguration().fontScale);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float width = paint.measureText(text);
        float height = metrics.descent - metrics.ascent;
        float scale = Math.min(1f, Math.min(maxWidth / Math.max(1f, width), maxHeight / Math.max(1f, height)));
        if (scale < 1f) paint.setTextSize(paint.getTextSize() * scale);
        metrics = paint.getFontMetrics();
        canvas.drawText(text, 0, centerY - (metrics.ascent + metrics.descent) / 2f, paint);
    }
}
