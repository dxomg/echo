package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.util.AttributeSet;

/** Compact checkable filter button using the app's legacy checked-state machinery. */
public class EchoChip extends EchoButton {
    public EchoChip(Context context) { this(context, null); }
    public EchoChip(Context context, AttributeSet attrs) {
        super(context, attrs);
        setCheckable(true);
        setSingleLine(true);
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        setPadding(padding, getPaddingTop(), padding, getPaddingBottom());
    }
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        if (checkedListener != null) removeOnCheckedChangeListener(checkedListener);
        checkedListener = listener;
        if (listener != null) addOnCheckedChangeListener(listener);
    }
    private OnCheckedChangeListener checkedListener;
}
