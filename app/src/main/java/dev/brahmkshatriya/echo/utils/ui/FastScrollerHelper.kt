package dev.brahmkshatriya.echo.utils.ui

import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import dev.brahmkshatriya.echo.utils.ContextUtils.getSettings

object FastScrollerHelper {
    const val SCROLL_BAR = "scroll_bar"
    fun View.isScrollBarEnabled() = context.getSettings().getBoolean(SCROLL_BAR, false)

    fun applyTo(view: RecyclerView): FastScroller? = applyTo(view as View)

    fun applyTo(view: NestedScrollView): FastScroller? = applyTo(view as View)

    private fun applyTo(view: View): FastScroller? {
        view.isVerticalScrollBarEnabled = false
        if (!view.isScrollBarEnabled()) return null
        return FastScroller.attach(view)
    }
}
