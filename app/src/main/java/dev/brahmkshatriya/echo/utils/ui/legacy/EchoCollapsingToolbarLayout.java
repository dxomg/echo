package dev.brahmkshatriya.echo.utils.ui.legacy;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.view.View;
import androidx.appcompat.widget.Toolbar;

/** Keeps the toolbar pinned while scaling/fading the expanded title content. */
public class EchoCollapsingToolbarLayout extends FrameLayout {
    private CharSequence title;
    public EchoCollapsingToolbarLayout(Context context) { this(context, null); }
    public EchoCollapsingToolbarLayout(Context context, AttributeSet attrs) { super(context, attrs); }
    public CharSequence getTitle() { return title; }
    public void setTitle(CharSequence value) {
        title = value;
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i) instanceof Toolbar) ((Toolbar) getChildAt(i)).setTitle(value);
    }
    void setCollapseOffset(int offset, int range) {
        float fraction = range == 0 ? 1 : Math.min(1, (float) offset / range);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof Toolbar) child.setTranslationY(offset);
            else { child.setAlpha(1 - fraction); child.setTranslationY(offset * 0.5f); }
        }
    }
}
