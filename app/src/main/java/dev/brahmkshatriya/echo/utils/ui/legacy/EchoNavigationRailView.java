package dev.brahmkshatriya.echo.utils.ui.legacy;
import android.content.Context;
import android.util.AttributeSet;
public class EchoNavigationRailView extends EchoNavigationBarView {
    public EchoNavigationRailView(Context context) { this(context, null); }
    public EchoNavigationRailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        rebuild();
    }
}
