package dev.brahmkshatriya.echo.utils.ui.legacy;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import java.util.ArrayList;
import java.util.List;

/** Collapsible header driven by CoordinatorLayout nested scrolling on API18. */
public class EchoAppBarLayout extends LinearLayout implements CoordinatorLayout.AttachedBehavior {
    public interface OnOffsetChangedListener { void onOffsetChanged(EchoAppBarLayout bar, int offset); }
    private final List<OnOffsetChangedListener> listeners = new ArrayList<>();
    private final Behavior behavior = new Behavior();
    private int offset;
    public EchoAppBarLayout(Context context) { this(context, null); }
    public EchoAppBarLayout(Context context, AttributeSet attrs) { super(context, attrs); setOrientation(VERTICAL); }
    public int getTotalScrollRange() {
        int toolbar = Math.round(56 * getResources().getDisplayMetrics().density);
        return Math.max(0, getHeight() - toolbar);
    }
    public void addOnOffsetChangedListener(OnOffsetChangedListener listener) { listeners.add(listener); }
    public void removeOnOffsetChangedListener(OnOffsetChangedListener listener) { listeners.remove(listener); }
    public void setExpanded(boolean expanded) { setExpanded(expanded, true); }
    public void setExpanded(boolean expanded, boolean animate) {
        final int target = expanded ? 0 : -getTotalScrollRange();
        if (!animate) setOffset(target);
        else {
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(offset, target);
            animator.setDuration(220);
            animator.addUpdateListener(value -> setOffset((Integer) value.getAnimatedValue()));
            animator.start();
        }
    }
    private void setOffset(int next) {
        offset = Math.max(-getTotalScrollRange(), Math.min(0, next));
        setTranslationY(offset);
        if (getParent() instanceof CoordinatorLayout) ((CoordinatorLayout) getParent()).dispatchDependentViewsChanged(this);
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i) instanceof EchoCollapsingToolbarLayout)
            ((EchoCollapsingToolbarLayout) getChildAt(i)).setCollapseOffset(-offset, getTotalScrollRange());
        for (OnOffsetChangedListener listener : listeners) listener.onOffsetChanged(this, offset);
    }
    @Override public CoordinatorLayout.Behavior<EchoAppBarLayout> getBehavior() { return behavior; }
    public static class Behavior extends CoordinatorLayout.Behavior<EchoAppBarLayout> {
        public Behavior() { }
        public Behavior(Context context, AttributeSet attrs) { super(context, attrs); }
        @Override public boolean onStartNestedScroll(CoordinatorLayout parent, EchoAppBarLayout child, View direct, View target, int axes, int type) {
            return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
        }
        @Override public void onNestedPreScroll(CoordinatorLayout parent, EchoAppBarLayout child, View target, int dx, int dy, int[] consumed, int type) {
            if (dy > 0 || !ViewCompat.canScrollVertically(target, -1)) {
                int old = child.offset;
                child.setOffset(old - dy);
                consumed[1] = old - child.offset;
            }
        }
        @Override public void onStopNestedScroll(CoordinatorLayout parent, EchoAppBarLayout child, View target, int type) {
            child.setExpanded(-child.offset < child.getTotalScrollRange() / 2);
        }
    }
    public static class ScrollingViewBehavior extends CoordinatorLayout.Behavior<View> {
        public ScrollingViewBehavior() { }
        public ScrollingViewBehavior(Context context, AttributeSet attrs) { super(context, attrs); }
        @Override public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) { return dependency instanceof EchoAppBarLayout; }
        @Override public boolean onDependentViewChanged(CoordinatorLayout parent, View child, View dependency) {
            int desired = dependency.getBottom() + Math.round(dependency.getTranslationY());
            ViewCompat.offsetTopAndBottom(child, desired - child.getTop());
            return true;
        }
        @Override public boolean onLayoutChild(CoordinatorLayout parent, View child, int direction) {
            parent.onLayoutChild(child, direction);
            for (View dependency : parent.getDependencies(child)) if (dependency instanceof EchoAppBarLayout) onDependentViewChanged(parent, child, dependency);
            return true;
        }
    }
}
