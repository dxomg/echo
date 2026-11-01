package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.customview.view.AbsSavedState;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import dev.brahmkshatriya.echo.R;

/** API18 coordinator sheet: dragging, nested scrolling, settling and saved state. */
public class EchoBottomSheetBehavior<V extends View> extends CoordinatorLayout.Behavior<V> {
    public static final int STATE_DRAGGING = 1, STATE_SETTLING = 2, STATE_EXPANDED = 3,
            STATE_COLLAPSED = 4, STATE_HIDDEN = 5, STATE_HALF_EXPANDED = 6;
    private int state = STATE_COLLAPSED, peekHeight;
    private boolean hideable, draggable = true;
    private WeakReference<V> sheet = new WeakReference<>(null);
    private final List<BottomSheetCallback> callbacks = new ArrayList<>();
    private float downY, lastY, startTranslation;
    private boolean dragging;
    private int touchSlop;
    public EchoBottomSheetBehavior() { }
    public EchoBottomSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EchoBottomSheetBehavior);
        peekHeight = a.getDimensionPixelSize(R.styleable.EchoBottomSheetBehavior_behavior_peekHeight, 0);
        hideable = a.getBoolean(R.styleable.EchoBottomSheetBehavior_behavior_hideable, false);
        draggable = a.getBoolean(R.styleable.EchoBottomSheetBehavior_behavior_draggable, true);
        state = a.getInt(R.styleable.EchoBottomSheetBehavior_behavior_state, STATE_COLLAPSED);
        a.recycle();
    }
    public abstract static class BottomSheetCallback {
        public abstract void onStateChanged(View sheet, int state);
        public abstract void onSlide(View sheet, float offset);
    }
    public void addBottomSheetCallback(BottomSheetCallback callback) { callbacks.add(callback); }
    public void removeBottomSheetCallback(BottomSheetCallback callback) { callbacks.remove(callback); }
    public int getState() { return state; }
    public void setState(int next) {
        if (next == STATE_HIDDEN && !hideable) next = STATE_COLLAPSED;
        if (next == STATE_DRAGGING || next == STATE_SETTLING) return;
        final int target = next;
        V view = sheet.get();
        if (view == null || !ViewCompat.isLaidOut(view)) { state = next; return; }
        view.animate().cancel();
        notifyState(view, STATE_SETTLING);
        view.animate().translationY(position(view, target)).setDuration(220)
                .withEndAction(() -> { notifyState(view, target); notifySlide(view); }).start();
        view.postOnAnimation(new Runnable() {
            public void run() {
                notifySlide(view);
                if (state == STATE_SETTLING) view.postOnAnimation(this);
            }
        });
    }
    private float collapsed(View view) { return Math.max(0, view.getHeight() - peekHeight); }
    private float position(View view, int next) {
        if (next == STATE_EXPANDED) return 0;
        if (next == STATE_HIDDEN) return view.getHeight();
        if (next == STATE_HALF_EXPANDED) return collapsed(view) / 2;
        return collapsed(view);
    }
    private void notifyState(View view, int next) {
        state = next;
        for (BottomSheetCallback callback : new ArrayList<>(callbacks)) callback.onStateChanged(view, next);
    }
    private void notifySlide(View view) {
        float collapsed = collapsed(view), y = view.getTranslationY();
        float offset = y <= collapsed ? (collapsed == 0 ? 1 : 1 - y / collapsed)
                : -(y - collapsed) / Math.max(1, view.getHeight() - collapsed);
        for (BottomSheetCallback callback : new ArrayList<>(callbacks)) callback.onSlide(view, offset);
    }
    public int getPeekHeight() { return peekHeight; }
    public void setPeekHeight(int height) {
        peekHeight = Math.max(0, height);
        V view = sheet.get();
        if (view != null) { view.requestLayout(); }
    }
    public boolean isHideable() { return hideable; }
    public void setHideable(boolean value) { hideable = value; }
    public boolean isDraggable() { return draggable; }
    public void setDraggable(boolean value) { draggable = value; }
    @Override public boolean onLayoutChild(CoordinatorLayout parent, V child, int direction) {
        parent.onLayoutChild(child, direction);
        sheet = new WeakReference<>(child);
        if (touchSlop == 0) touchSlop = ViewConfiguration.get(child.getContext()).getScaledTouchSlop();
        if (state != STATE_DRAGGING && state != STATE_SETTLING) child.setTranslationY(position(child, state));
        notifySlide(child);
        return true;
    }
    @Override public boolean onInterceptTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        if (!draggable || !child.isShown()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downY = lastY = event.getY();
            startTranslation = child.getTranslationY();
            dragging = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float delta = event.getY() - downY;
            if (Math.abs(delta) > touchSlop && parent.isPointInChildBounds(child, (int) event.getX(), (int) downY)
                    && (startTranslation > 0 || delta > 0 && !canScrollUp(child, event.getX(), event.getY()))) {
                child.animate().cancel();
                dragging = true;
                notifyState(child, STATE_DRAGGING);
            }
        }
        return dragging;
    }
    private boolean canScrollUp(View view, float x, float y) {
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE && x >= child.getLeft() && x < child.getRight()
                        && y >= child.getTop() && y < child.getBottom()
                        && canScrollUp(child, x - child.getLeft(), y - child.getTop())) return true;
            }
        }
        return ViewCompat.canScrollVertically(view, -1);
    }
    @Override public boolean onTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        if (!draggable) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float y = event.getY();
            move(child, y - lastY);
            lastY = y;
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            settle(child, event.getY() - downY);
            dragging = false;
        }
        return true;
    }
    private void move(V view, float delta) {
        view.setTranslationY(Math.max(0, Math.min(hideable ? view.getHeight() : collapsed(view), view.getTranslationY() + delta)));
        notifySlide(view);
    }
    private void settle(V view, float direction) {
        float y = view.getTranslationY(), collapsed = collapsed(view);
        setState(hideable && y > collapsed + peekHeight / 2f ? STATE_HIDDEN
                : direction < -touchSlop ? STATE_EXPANDED
                : direction > touchSlop ? STATE_COLLAPSED
                : y < collapsed / 2 ? STATE_EXPANDED : STATE_COLLAPSED);
    }
    @Override public boolean onStartNestedScroll(CoordinatorLayout parent, V child, View direct, View target, int axes, int type) {
        return draggable && (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }
    @Override public void onNestedPreScroll(CoordinatorLayout parent, V child, View target, int dx, int dy, int[] consumed, int type) {
        if (type != ViewCompat.TYPE_TOUCH) return;
        if (dy > 0 && child.getTranslationY() > 0 || dy < 0 && !ViewCompat.canScrollVertically(target, -1)) {
            float old = child.getTranslationY();
            move(child, -dy);
            consumed[1] = Math.round(old - child.getTranslationY());
            notifyState(child, STATE_DRAGGING);
        }
    }
    @Override public void onStopNestedScroll(CoordinatorLayout parent, V child, View target, int type) {
        if (state == STATE_DRAGGING) settle(child, 0);
    }
    @SuppressWarnings("unchecked") public static <V extends View> EchoBottomSheetBehavior<V> from(V view) {
        if (!(view.getLayoutParams() instanceof CoordinatorLayout.LayoutParams)) throw new IllegalArgumentException("Sheet requires CoordinatorLayout");
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) view.getLayoutParams();
        if (!(params.getBehavior() instanceof EchoBottomSheetBehavior)) throw new IllegalArgumentException("Sheet behavior missing");
        return (EchoBottomSheetBehavior<V>) params.getBehavior();
    }
    @Override public Parcelable onSaveInstanceState(CoordinatorLayout parent, V child) {
        return new SavedState(super.onSaveInstanceState(parent, child), state);
    }
    @Override public void onRestoreInstanceState(CoordinatorLayout parent, V child, Parcelable value) {
        if (!(value instanceof SavedState)) { super.onRestoreInstanceState(parent, child, value); return; }
        SavedState saved = (SavedState) value;
        super.onRestoreInstanceState(parent, child, saved.getSuperState());
        state = saved.state == STATE_DRAGGING || saved.state == STATE_SETTLING ? STATE_COLLAPSED : saved.state;
    }
    private static class SavedState extends AbsSavedState {
        final int state;
        SavedState(Parcelable parent, int state) { super(parent); this.state = state; }
        SavedState(Parcel source, ClassLoader loader) { super(source, loader); state = source.readInt(); }
        @Override public void writeToParcel(Parcel dest, int flags) { super.writeToParcel(dest, flags); dest.writeInt(state); }
        public static final Parcelable.ClassLoaderCreator<SavedState> CREATOR = new Parcelable.ClassLoaderCreator<SavedState>() {
            public SavedState createFromParcel(Parcel source, ClassLoader loader) { return new SavedState(source, loader); }
            public SavedState createFromParcel(Parcel source) { return new SavedState(source, null); }
            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }
}
