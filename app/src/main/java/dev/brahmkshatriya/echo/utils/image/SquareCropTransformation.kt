package dev.brahmkshatriya.echo.utils.image

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class SquareCropTransformation : BitmapTransformation() {

    val version = 1

    override fun transform(
        pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int,
    ): Bitmap {
        val max = toTransform.width.coerceAtMost(toTransform.height)
        if (max <= 0 || toTransform.isRecycled) return toTransform
        val x = (toTransform.width - max) / 2
        val y = (toTransform.height - max) / 2
        return Bitmap.createBitmap(toTransform, x, y, max, max)
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(("$javaClass.${version}").toByteArray())
    }
}
