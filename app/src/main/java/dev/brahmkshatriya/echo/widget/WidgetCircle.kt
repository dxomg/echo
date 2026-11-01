package dev.brahmkshatriya.echo.widget

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.google.android.exoplayer2.Player
import dev.brahmkshatriya.echo.R

class WidgetCircle : BaseWidget() {
    override val clazz = this::class.java
    override fun updatedViews(
        controller: Player?, image: Bitmap?, context: Context, appWidgetId: Int
    ): RemoteViews {
        val packageName = context.packageName
        val view = RemoteViews(packageName, R.layout.widget_circle)
        updateView(clazz, controller, image, context, view)
        return view
    }
}
