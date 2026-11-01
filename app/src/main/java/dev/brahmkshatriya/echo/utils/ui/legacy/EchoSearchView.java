package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatImageButton;
import java.util.ArrayList;
import java.util.List;

/** Inline full-screen search with a real editor, results children and back affordance. */
public class EchoSearchView extends LinearLayout {
    public enum TransitionState { HIDDEN, HIDING, SHOWING, SHOWN }
    public interface TransitionListener {
        void onStateChanged(EchoSearchView view, TransitionState previous, TransitionState current);
    }
    private TransitionState state = TransitionState.HIDDEN;
    private final List<TransitionListener> listeners = new ArrayList<>();
    private final AppCompatEditText editor;
    public EchoSearchView(Context context) { this(context, null); }
    public EchoSearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setBackgroundColor(EchoColors.getColor(context,
                dev.brahmkshatriya.echo.R.attr.echoBackground, 0xff202020));
        LinearLayout header = new LinearLayout(context);
        AppCompatImageButton back = new AppCompatImageButton(context);
        back.setImageResource(dev.brahmkshatriya.echo.R.drawable.ic_back);
        back.setContentDescription(context.getString(dev.brahmkshatriya.echo.R.string.back));
        back.setOnClickListener(v -> hide());
        int size = Math.round(48 * getResources().getDisplayMetrics().density);
        header.addView(back, new LinearLayout.LayoutParams(size, size));
        editor = new AppCompatEditText(context);
        editor.setSingleLine(true);
        editor.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        header.addView(editor, new LinearLayout.LayoutParams(0, size, 1));
        super.addView(header, new LayoutParams(-1, -2));
        setVisibility(GONE);
    }
    public AppCompatEditText getEditText() { return editor; }
    public CharSequence getText() { return editor.getText(); }
    public void setText(CharSequence text) { editor.setText(text); }
    public TransitionState getCurrentTransitionState() { return state; }
    public boolean isShowing() { return state == TransitionState.SHOWN || state == TransitionState.SHOWING; }
    public void addTransitionListener(TransitionListener listener) { listeners.add(listener); }
    public void removeTransitionListener(TransitionListener listener) { listeners.remove(listener); }
    private void transition(TransitionState next) {
        TransitionState old = state;
        state = next;
        for (TransitionListener listener : new ArrayList<>(listeners)) listener.onStateChanged(this, old, next);
    }
    public void setupWithSearchBar(EchoSearchBar bar) {
        editor.setHint(bar.getHint());
        bar.setOnClickListener(v -> show());
    }
    public void show() {
        if (isShowing()) return;
        animate().cancel();
        transition(TransitionState.SHOWING);
        setVisibility(VISIBLE);
        setAlpha(0f);
        animate().alpha(1f).setDuration(150).withEndAction(() -> transition(TransitionState.SHOWN));
        editor.requestFocus();
        editor.post(() -> {
            InputMethodManager ime = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (ime != null) ime.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        });
    }
    public void hide() {
        if (state == TransitionState.HIDDEN || state == TransitionState.HIDING) return;
        animate().cancel();
        transition(TransitionState.HIDING);
        InputMethodManager ime = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (ime != null) ime.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        editor.clearFocus();
        animate().alpha(0f).setDuration(150).withEndAction(() -> {
            setVisibility(GONE);
            transition(TransitionState.HIDDEN);
        });
    }
}
