package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.view.Gravity;
import androidx.transition.Fade;
import androidx.transition.Slide;
import androidx.transition.TransitionSet;

/** Legacy-compatible fade/slide navigation transitions. */
public final class EchoSharedAxis extends TransitionSet {
    public static final int X = 0, Y = 1, Z = 2;
    public EchoSharedAxis(int axis, boolean forward) {
        setOrdering(ORDERING_TOGETHER);
        addTransition(new Fade());
        if (axis == X) addTransition(new Slide(forward ? Gravity.END : Gravity.START));
        else if (axis == Y) addTransition(new Slide(forward ? Gravity.BOTTOM : Gravity.TOP));
        setDuration(350);
    }
}
