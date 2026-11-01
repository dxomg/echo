package dev.brahmkshatriya.echo.utils.ui.legacy;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import java.util.ArrayList;
import java.util.List;

/** Accessible in-app transient message with action, timeout and dismissal callbacks. */
public class EchoSnackbar {
    public static final int LENGTH_LONG = 0, LENGTH_SHORT = -1, LENGTH_INDEFINITE = -2, ANIMATION_MODE_SLIDE = 0;
    public abstract static class Callback {
        public static final int DISMISS_EVENT_SWIPE = 0, DISMISS_EVENT_ACTION = 1, DISMISS_EVENT_TIMEOUT = 2,
                DISMISS_EVENT_MANUAL = 3, DISMISS_EVENT_CONSECUTIVE = 4;
        public void onDismissed(EchoSnackbar snackbar, int event) { }
    }
    private final LinearLayout view;
    private final ViewGroup parent;
    private final int duration;
    private final List<Callback> callbacks = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> dismiss(Callback.DISMISS_EVENT_TIMEOUT);
    private int animationMode;
    private boolean shown;
    private EchoSnackbar(View anchor, CharSequence message, int duration) {
        View current = anchor;
        ViewGroup host = null;
        while (current != null) {
            if (current instanceof CoordinatorLayout || current instanceof FrameLayout) { host = (ViewGroup) current; break; }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        if (host == null && anchor instanceof ViewGroup) host = (ViewGroup) anchor;
        if (host == null) throw new IllegalArgumentException("Snackbar requires a container");
        parent = host;
        this.duration = duration;
        view = new LinearLayout(anchor.getContext());
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setBackgroundColor(0xff323232);
        int padding = Math.round(12 * anchor.getResources().getDisplayMetrics().density);
        view.setPadding(padding, padding, padding, padding);
        AppCompatTextView text = new AppCompatTextView(anchor.getContext());
        text.setText(message);
        text.setTextColor(0xffffffff);
        view.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        if (parent instanceof CoordinatorLayout) {
            CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(-1, -2);
            params.gravity = Gravity.BOTTOM;
            view.setLayoutParams(params);
        } else {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            view.setLayoutParams(params);
        }
        androidx.core.view.ViewCompat.setAccessibilityLiveRegion(view, androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    public static EchoSnackbar make(View anchor, CharSequence message, int duration) { return new EchoSnackbar(anchor, message, duration); }
    public View getView() { return view; }
    public int getAnimationMode() { return animationMode; }
    public void setAnimationMode(int mode) { animationMode = mode; }
    public EchoSnackbar addCallback(Callback callback) { callbacks.add(callback); return this; }
    public EchoSnackbar setAction(CharSequence title, View.OnClickListener listener) {
        EchoButton action = new EchoButton(view.getContext());
        action.setText(title);
        action.setOnClickListener(v -> { listener.onClick(v); dismiss(Callback.DISMISS_EVENT_ACTION); });
        view.addView(action, new LinearLayout.LayoutParams(-2, -2));
        return this;
    }
    public void show() {
        if (shown) return;
        shown = true;
        parent.addView(view);
        view.post(() -> { view.setTranslationY(view.getHeight()); view.animate().translationY(0).setDuration(175).start(); });
        if (duration != LENGTH_INDEFINITE) handler.postDelayed(timeout, duration > 0 ? duration : duration == LENGTH_SHORT ? 2000 : 5000);
    }
    public void dismiss() { dismiss(Callback.DISMISS_EVENT_MANUAL); }
    private void dismiss(int event) {
        if (!shown) return;
        shown = false;
        handler.removeCallbacks(timeout);
        view.animate().translationY(view.getHeight()).setDuration(175).withEndAction(() -> {
            parent.removeView(view);
            for (Callback callback : callbacks) callback.onDismissed(this, event);
        }).start();
    }
}
