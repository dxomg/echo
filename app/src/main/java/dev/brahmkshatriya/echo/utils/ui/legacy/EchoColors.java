package dev.brahmkshatriya.echo.utils.ui.legacy;
import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
/** Theme color resolution without a Material runtime. */
public final class EchoColors {
    private static final java.util.Map<Context, Integer> accents = new java.util.WeakHashMap<>();
    private EchoColors() { }
    public static void setAccent(Context context, Integer color) {
        if (color == null) accents.remove(context);
        else accents.put(context, color);
    }
    public static int getColor(View view, int attr) { return getColor(view, attr, 0); }
    public static int getColor(View view, int attr, int fallback) { return getColor(view.getContext(), attr, fallback); }
    public static int getColor(Context context, int attr, int fallback) {
        Context current = context;
        while (current != null) {
            Integer accent = accents.get(current);
            if (accent != null && (attr == androidx.appcompat.R.attr.colorPrimary
                    || attr == androidx.appcompat.R.attr.colorAccent)) return accent;
            if (!(current instanceof android.content.ContextWrapper)) break;
            Context base = ((android.content.ContextWrapper) current).getBaseContext();
            if (base == current) break;
            current = base;
        }
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) return fallback;
        return value.resourceId != 0 ? ContextCompat.getColor(context, value.resourceId) : value.data;
    }
    public static int harmonize(int color, int accent) { return ColorUtils.blendARGB(color, accent, 0.15f); }
}
