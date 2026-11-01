package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

/** Search entry button; opening and editing are handled by EchoSearchView. */
public class EchoSearchBar extends AppCompatTextView {
    private final CharSequence hint;
    public EchoSearchBar(Context context) { this(context, null); }
    public EchoSearchBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        hint = getHint();
        setClickable(true);
        setFocusable(true);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        setCompoundDrawablesWithIntrinsicBounds(dev.brahmkshatriya.echo.R.drawable.ic_search_outline, 0, 0, 0);
        setCompoundDrawablePadding(padding);
    }
    @Override public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        setContentDescription(text == null || text.length() == 0 ? hint : text);
    }
}
