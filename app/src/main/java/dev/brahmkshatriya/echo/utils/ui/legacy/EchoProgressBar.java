package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ClipDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ProgressBar;
import dev.brahmkshatriya.echo.R;

/** Legacy horizontal progress bar with runtime tint support. */
public class EchoProgressBar extends ProgressBar {
    private int indicatorColor, trackColor;
    private int thickness;
    private final boolean horizontal;
    public EchoProgressBar(Context c) { this(c, null); }
    public EchoProgressBar(Context c, AttributeSet attrs) {
        this(c, attrs, android.R.attr.progressBarStyleHorizontal);
    }
    protected EchoProgressBar(Context c, AttributeSet attrs, int style) {
        super(c, attrs, style);
        horizontal = style == android.R.attr.progressBarStyleHorizontal;
        TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.EchoProgressBar);
        indicatorColor = a.getColor(R.styleable.EchoProgressBar_indicatorColor, EchoColors.getColor(this, androidx.appcompat.R.attr.colorAccent));
        trackColor = a.getColor(R.styleable.EchoProgressBar_trackColor, 0x33000000);
        thickness = a.getDimensionPixelSize(R.styleable.EchoProgressBar_trackThickness, Math.round(4 * getResources().getDisplayMetrics().density));
        a.recycle();
        updateTrack();
        tintIndicator();
    }
    private GradientDrawable color(int color) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setSize(1, thickness); return d;
    }
    private void updateTrack() {
        if (!horizontal) {
            setProgressDrawable(new CircularTrack());
            return;
        }
        LayerDrawable d = new LayerDrawable(new Drawable[]{color(trackColor), new ClipDrawable(color(indicatorColor), Gravity.LEFT, ClipDrawable.HORIZONTAL)});
        d.setId(0, android.R.id.background); d.setId(1, android.R.id.progress);
        setProgressDrawable(d);
    }
    private void tintIndicator() {
        if (getIndeterminateDrawable() != null) getIndeterminateDrawable().mutate().setColorFilter(indicatorColor, PorterDuff.Mode.SRC_IN);
    }
    public void setIndicatorColor(int color) { indicatorColor = color; updateTrack(); tintIndicator(); }
    public int getTrackColor() { return trackColor; }
    public void setTrackColor(int color) { trackColor = color; updateTrack(); }
    public void show() { setVisibility(VISIBLE); }
    public void hide() { setVisibility(GONE); }

    /** Determinate circular progress is not supplied by the pre-Lollipop theme. */
    private final class CircularTrack extends Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF oval = new android.graphics.RectF();
        CircularTrack() { paint.setStyle(android.graphics.Paint.Style.STROKE); }
        @Override public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float stroke = Math.max(1, thickness);
            float radius = Math.max(0, (Math.min(b.width(), b.height()) - stroke) / 2f);
            oval.set(b.exactCenterX() - radius, b.exactCenterY() - radius,
                    b.exactCenterX() + radius, b.exactCenterY() + radius);
            paint.setStrokeWidth(stroke);
            paint.setColor(trackColor);
            canvas.drawOval(oval, paint);
            paint.setColor(indicatorColor);
            canvas.drawArc(oval, -90, 360f * getLevel() / 10000f, false, paint);
        }
        @Override protected boolean onLevelChange(int level) { invalidateSelf(); return true; }
        @Override public int getIntrinsicWidth() { return Math.round(36 * getResources().getDisplayMetrics().density); }
        @Override public int getIntrinsicHeight() { return getIntrinsicWidth(); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
