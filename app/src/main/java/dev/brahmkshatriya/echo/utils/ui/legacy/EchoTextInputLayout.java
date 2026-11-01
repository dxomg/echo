package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.widget.AppCompatTextView;

/** Floating label and validation error container for AppCompat editable fields. */
public class EchoTextInputLayout extends LinearLayout {
    private final AppCompatTextView label, errorLabel;
    private CharSequence error;
    public EchoTextInputLayout(Context context) { this(context, null); }
    public EchoTextInputLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        label = new AppCompatTextView(context);
        label.setTextSize(12);
        errorLabel = new AppCompatTextView(context);
        errorLabel.setTextColor(0xffb00020);
        errorLabel.setTextSize(12);
        errorLabel.setVisibility(GONE);
        android.content.res.TypedArray a = context.obtainStyledAttributes(attrs,
                new int[]{android.R.attr.hint});
        label.setText(a.getText(0));
        a.recycle();
        if (label.getText().length() == 0) label.setVisibility(GONE);
        super.addView(label, 0, new LayoutParams(-1, -2));
    }
    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        super.addView(errorLabel, new LayoutParams(-1, -2));
    }
    public EditText getEditText() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof EditText) return (EditText) child;
        }
        return null;
    }
    public CharSequence getError() { return error; }
    public void setError(CharSequence text) {
        error = text;
        errorLabel.setText(text);
        errorLabel.setVisibility(text == null || text.length() == 0 ? GONE : VISIBLE);
        if (text != null) errorLabel.announceForAccessibility(text);
    }
    public void setHint(CharSequence text) {
        label.setText(text);
        label.setVisibility(text == null || text.length() == 0 ? GONE : VISIBLE);
    }
}
