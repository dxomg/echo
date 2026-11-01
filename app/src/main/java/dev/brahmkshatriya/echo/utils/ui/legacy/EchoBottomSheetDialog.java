package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import androidx.appcompat.app.AppCompatDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import android.widget.FrameLayout;

/** Bottom-gravity dialog hosting the same draggable sheet used by the player. */
public class EchoBottomSheetDialog extends AppCompatDialog {
    private final EchoBottomSheetBehavior<FrameLayout> behavior = new EchoBottomSheetBehavior<>();
    public EchoBottomSheetDialog(Context context, int theme) {
        super(context, theme == 0 ? dev.brahmkshatriya.echo.R.style.EchoBottomSheetDialog : theme);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        behavior.setHideable(true);
        behavior.addBottomSheetCallback(new EchoBottomSheetBehavior.BottomSheetCallback() {
            public void onStateChanged(View view, int state) { if (state == EchoBottomSheetBehavior.STATE_HIDDEN) cancel(); }
            public void onSlide(View view, float offset) { }
        });
    }
    public EchoBottomSheetBehavior<FrameLayout> getBehavior() { return behavior; }
    @Override public void setContentView(View content) { setContentView(content, new ViewGroup.LayoutParams(-1, -2)); }
    @Override public void setContentView(View content, ViewGroup.LayoutParams params) {
        CoordinatorLayout root = new CoordinatorLayout(getContext());
        FrameLayout sheet = new FrameLayout(getContext());
        sheet.setBackgroundResource(dev.brahmkshatriya.echo.R.drawable.dialog_bg);
        sheet.addView(content, params);
        CoordinatorLayout.LayoutParams layout = new CoordinatorLayout.LayoutParams(-1, -1);
        layout.gravity = Gravity.BOTTOM;
        layout.setBehavior(behavior);
        root.addView(sheet, layout);
        behavior.setState(EchoBottomSheetBehavior.STATE_EXPANDED);
        super.setContentView(root);
    }
    @Override protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(-1, Math.round(getContext().getResources().getDisplayMetrics().heightPixels * 0.9f));
        }
    }
}
