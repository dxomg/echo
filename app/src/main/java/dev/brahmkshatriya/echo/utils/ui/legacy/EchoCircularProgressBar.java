package dev.brahmkshatriya.echo.utils.ui.legacy;
import android.content.Context;
import android.util.AttributeSet;
public class EchoCircularProgressBar extends EchoProgressBar {
    public EchoCircularProgressBar(Context c) { this(c, null); }
    public EchoCircularProgressBar(Context c, AttributeSet a) { super(c, a, android.R.attr.progressBarStyleSmall); }
}
