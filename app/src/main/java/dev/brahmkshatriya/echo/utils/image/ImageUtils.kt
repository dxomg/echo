package dev.brahmkshatriya.echo.utils.image

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.toColorInt
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.Transformation
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.signature.ObjectKey
import dev.brahmkshatriya.echo.common.models.ImageHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageUtils {

    private fun <T> tryWith(print: Boolean = false, block: () -> T): T? {
        return try {
            block()
        } catch (e: Throwable) {
            if (print) e.printStackTrace()
            null
        }
    }

    private suspend fun <T> tryWithSuspend(print: Boolean = true, block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: Throwable) {
            if (print) e.printStackTrace()
            null
        }
    }

    private val circleCrop = CircleCrop()

    fun ImageHolder?.loadInto(
        imageView: ImageView, placeholder: Int? = null, errorDrawable: Int? = null
    ) = tryWith {
        createRequest(imageView.context, placeholder, errorDrawable)?.into(imageView)
    }

    fun ImageHolder.getCachedDrawable(context: Context): Drawable? {
        return null
    }

    fun <T : View> ImageHolder?.loadWithThumb(
        view: T, thumbnail: Drawable? = null,
        error: Int? = null, onDrawable: T.(Drawable?) -> Unit
    ) = tryWith(true) {
        tryWith(false) { onDrawable(view, thumbnail) }
        createRequest(view.context, null, error)?.into(
            object : GlideDrawableTarget() {
                override fun onResolved(drawable: Drawable?) =
                    tryWith { view.onDrawable(drawable) }
            }
        )
    }

    fun <T : View> ImageHolder?.loadAsCircle(
        view: T,
        placeholder: Int? = null,
        error: Int? = null,
        onDrawable: (Drawable?) -> Unit
    ) = tryWith {
        createRequest(view.context, placeholder, error, circleCrop)?.into(
            object : GlideDrawableTarget() {
                override fun onResolved(drawable: Drawable?) = tryWith { onDrawable(drawable) }
            }
        )
    }

    private abstract class GlideDrawableTarget :
        com.bumptech.glide.request.target.CustomTarget<Drawable>(600, 600) {
        abstract fun onResolved(drawable: Drawable?)

        override fun onResourceReady(
            resource: Drawable, transition: Transition<in Drawable>?,
        ) = onResolved(resource)

        override fun onLoadCleared(placeholder: Drawable?) = onResolved(placeholder)

        override fun onLoadFailed(errorDrawable: Drawable?) = onResolved(null)
    }

    suspend fun ImageHolder?.loadDrawable(
        context: Context
    ) = tryWithSuspend {
        createRequest(context, null, null)?.await()
    }

    suspend fun ImageHolder?.loadAsCircleDrawable(
        context: Context
    ) = tryWithSuspend {
        createRequest(context, null, null, circleCrop)?.await()
    }

    /** Loads the drawable by attaching a custom target from the main thread. */
    private suspend fun RequestBuilder<Drawable>.await(): Drawable? =
        withContext(Dispatchers.Main) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                into(object : GlideDrawableTarget() {
                    override fun onResolved(drawable: Drawable?) {
                        if (cont.isActive) cont.resume(drawable, null)
                    }
                })
            }
        }

    fun ImageView.loadBlurred(drawable: Drawable?, radius: Float) = tryWith {
        when (drawable) {
            null -> setImageDrawable(null)
            else -> Glide.with(this).load(drawable)
                .transform(BlurTransformation(context, radius))
                .into(this)
        }
    }

    private val ImageHolder.diskId
        get() = when (this) {
            is ImageHolder.NetworkRequestImageHolder -> request.toString().hashCode().toString()
            else -> null
        }

    private fun ImageHolder?.createRequest(
        context: Context,
        placeholder: Int?,
        errorDrawable: Int?,
        transformation: Transformation<android.graphics.Bitmap>? = null,
    ): RequestBuilder<Drawable>? {
        val error = errorDrawable ?: placeholder
        if (this == null) {
            if (error == null) return null
            return Glide.with(context).asDrawable().load(error)
        }
        val builder = Glide.with(context).asDrawable()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        when (this) {
            is ImageHolder.ResourceUriImageHolder -> builder.load(uri)
            is ImageHolder.NetworkRequestImageHolder ->
                builder.load(GlideUrl(request.url, request.headers))
            is ImageHolder.ResourceIdImageHolder -> builder.load(resId)
            is ImageHolder.HexColorImageHolder -> builder.load(ColorDrawable(hex.toColorInt()))
        }
        diskId?.let { builder.signature(ObjectKey(it)) }
        placeholder?.let { builder.placeholder(it) }
        error?.let { builder.error(it) }
        when {
            crop -> builder.transform(circleCrop)
            transformation != null -> builder.transform(transformation)
        }
        return builder
    }
}
