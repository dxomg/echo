package dev.brahmkshatriya.echo.utils.ui.legacy;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

/** Slides controls away on forward scroll and restores them on reverse scroll. */
public class EchoHideOnScrollBehavior extends CoordinatorLayout.Behavior<View> {
    private int edge = 1;
    public EchoHideOnScrollBehavior() { }
    public EchoHideOnScrollBehavior(Context context, AttributeSet attrs) { super(context, attrs); }
    public void setViewEdge(int edge) { this.edge = edge; }
    public void slideOut(View view) { view.animate().translationY(edge == 1 ? view.getHeight() : -view.getHeight()).setDuration(175).start(); }
    public void slideIn(View view) { view.animate().translationY(0).setDuration(225).start(); }
    @Override public boolean onStartNestedScroll(CoordinatorLayout parent, View child, View direct, View target, int axes, int type) {
        return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }
    @Override public void onNestedScroll(CoordinatorLayout parent, View child, View target, int dx, int dy, int ux, int uy, int type) {
        if (dy > 0) slideOut(child); else if (dy < 0) slideIn(child);
    }
}
