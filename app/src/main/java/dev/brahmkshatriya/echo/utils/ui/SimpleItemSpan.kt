package dev.brahmkshatriya.echo.utils.ui

import android.content.Context
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import androidx.core.content.ContextCompat
import dev.brahmkshatriya.echo.R

class SimpleItemSpan(
    val context: Context,
    private val onItemClicked: () -> Unit
) : ClickableSpan() {

    override fun onClick(widget: View) {
        onItemClicked()
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = false
        context.run {
            ds.color = ContextCompat.getColor(this, R.color.button_player)
        }
    }
}
