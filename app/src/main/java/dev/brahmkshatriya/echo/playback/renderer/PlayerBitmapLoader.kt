package dev.brahmkshatriya.echo.playback.renderer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmapOrNull
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.utils.Serializer.toData
import dev.brahmkshatriya.echo.utils.image.ImageUtils.loadDrawable

/**
 * Loads notification/browsing artwork. Art URIs embed the serialized
 * [ImageHolder] as the `actual_data` query parameter (see MediaItemUtils),
 * which is decoded back into a bitmap here.
 */
class PlayerBitmapLoader(
    val context: Context,
) {

    suspend fun loadBitmap(uri: Uri): Bitmap? = run {
        val data = uri.getQueryParameter("actual_data")
            ?.toData<ImageHolder>()?.getOrNull() ?: return null
        data.loadDrawable(context)?.toBitmapOrNull()
    }
}
