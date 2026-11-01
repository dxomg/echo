package dev.brahmkshatriya.echo.utils.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import dev.brahmkshatriya.echo.utils.ui.UiUtils.dpToPx

/**
 * API18-compatible fast scrollbar. Draws a compact thumb over the scrollable
 * view and allows dragging it from the right edge. The upstream fastscroll
 * library declares minSdk 21, so the app owns this small implementation.
 */
class FastScroller private constructor(private val target: View) : View(target.context) {

    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbRect = RectF()
    private val contentInsets = Rect()
    private val thumbWidth = 4.dpToPx(context)
    private val minThumbHeight = 32.dpToPx(context)
    private val touchSlop = 24.dpToPx(context)
    private var dragging = false
    private var dragOffset = 0f

    init {
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorControlNormal))
        thumb.color = attributes.getColor(0, 0x80FFFFFF.toInt())
        attributes.recycle()

        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

        target.viewTreeObserver.addOnScrollChangedListener { invalidate() }
        target.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncBounds() }
    }

    private fun syncBounds() {
        val parent = parent as? ViewGroup ?: return
        if (target.parent !== parent) return
        x = target.x
        y = target.y
        val params = layoutParams ?: return
        if (params.width != target.width || params.height != target.height) {
            params.width = target.width
            params.height = target.height
            layoutParams = params
        }
        invalidate()
    }

    private fun scrollRange(): Int =
        (target.computeVerticalScrollRange() - target.computeVerticalScrollExtent()).coerceAtLeast(0)

    private fun thumbBounds(): RectF {
        val range = scrollRange()
        val available = height - contentInsets.top - contentInsets.bottom
        if (range <= 0 || available <= 0) return RectF()
        val proportion = target.computeVerticalScrollExtent().toFloat() /
            target.computeVerticalScrollRange().coerceAtLeast(1)
        val thumbHeight = (available * proportion).coerceAtLeast(minThumbHeight.toFloat())
            .coerceAtMost(available.toFloat())
        val travel = (available - thumbHeight).coerceAtLeast(0f)
        val offset = target.computeVerticalScrollOffset().toFloat() / range
        val top = contentInsets.top + travel * offset.coerceIn(0f, 1f)
        val right = width - contentInsets.right.toFloat()
        val left = right - thumbWidth
        return RectF(left, top, right, top + thumbHeight)
    }

    override fun onDraw(canvas: Canvas) {
        thumbRect.set(thumbBounds())
        if (thumbRect.isEmpty) return
        val radius = thumbWidth / 2f
        canvas.drawRoundRect(thumbRect, radius, radius, thumb)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bounds = thumbBounds()
        if (bounds.isEmpty) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val nearThumb = event.x >= bounds.left - touchSlop
                if (!nearThumb) return false
                dragging = true
                dragOffset = if (event.y in bounds.top..bounds.bottom) event.y - bounds.top
                else bounds.height() / 2f
                parent?.requestDisallowInterceptTouchEvent(true)
                scrollToTouch(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                scrollToTouch(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun scrollToTouch(y: Float) {
        val range = scrollRange()
        if (range <= 0) return
        val available = height - contentInsets.top - contentInsets.bottom
        val thumbHeight = thumbBounds().height().coerceAtLeast(minThumbHeight.toFloat())
        val travel = (available - thumbHeight).coerceAtLeast(1f)
        val thumbTop = (y - dragOffset - contentInsets.top).coerceIn(0f, travel)
        val desired = (thumbTop / travel * range).toInt()
        when (target) {
            is RecyclerView -> target.scrollBy(0, desired - target.computeVerticalScrollOffset())
            is NestedScrollView -> target.scrollTo(0, desired)
            else -> target.scrollTo(0, desired)
        }
        invalidate()
    }

    fun setPadding(insets: Rect) {
        contentInsets.set(insets)
        invalidate()
    }

    companion object {
        fun attach(target: View): FastScroller? {
            val parent = target.parent as? ViewGroup ?: return null
            val scroller = FastScroller(target)
            parent.addView(
                scroller,
                ViewGroup.LayoutParams(target.width.coerceAtLeast(0), target.height.coerceAtLeast(0))
            )
            scroller.syncBounds()
            return scroller
        }
    }
}
