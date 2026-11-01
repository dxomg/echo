package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import dev.brahmkshatriya.echo.R;

/** Ordinary, non-selecting group. Transparent dividers preserve signed gaps on API18. */
public class EchoButtonGroup extends LinearLayout {
    public EchoButtonGroup(Context context) {
        this(context, null);
    }

    public EchoButtonGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        android.content.res.TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.EchoButtonGroup);
        final int spacing = a.getDimensionPixelSize(R.styleable.EchoButtonGroup_spacing, 0);
        a.recycle();
        // LinearLayout includes signed divider dimensions in measurement/layout;
        // negative spacing preserves the overlapping settings button rows.
        if (spacing != 0) {
            setDividerDrawable(new Drawable() {
                @Override public void draw(Canvas canvas) { }
                @Override public void setAlpha(int alpha) { }
                @Override public void setColorFilter(ColorFilter filter) { }
                @Override public int getOpacity() { return PixelFormat.TRANSPARENT; }
                @Override public int getIntrinsicWidth() { return spacing; }
                @Override public int getIntrinsicHeight() { return spacing; }
            });
            setShowDividers(SHOW_DIVIDER_MIDDLE);
        }
    }
}
