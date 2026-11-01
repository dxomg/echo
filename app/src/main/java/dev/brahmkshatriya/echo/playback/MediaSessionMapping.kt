package dev.brahmkshatriya.echo.playback

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.TIME_UNSET
import com.google.android.exoplayer2.MediaItem

/**
 * Bridges the app state carrier across the MediaSessionCompat world.
 *
 * The app attaches playlist state via MediaMetadata extras (see MediaItemUtils).
 * exo2 MediaMetadata keeps that Bundle natively; MediaMetadataCompat carries
 * custom string/number keys natively too, so the serialized state travels
 * alongside it between the service and in-process consumers.
 */
object MediaSessionMapping {

    fun MediaMetadataCompat.setCustomExtra(bundle: Bundle) = bundle.keySet().forEach { key ->
        when (val value = bundle.get(key)) {
            null -> {}
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Boolean -> putBoolean(key, value)
            is Double -> putDouble(key, value)
            is Float -> putFloat(key, value)
            else -> {}
        }
    }

    private fun android.os.Bundle.filterSupported() = Bundle().apply {
        keySet().forEach { key ->
            when (val value = get(key)) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Boolean -> putBoolean(key, value)
                is Double -> putDouble(key, value)
                is Float -> putFloat(key, value)
                else -> {}
            }
        }
    }

    /** Maps an exo2 MediaMetadata onto compat metadata and mirrors custom extras. */
    fun toCompatMetadata(item: MediaItem, durationMs: Long = TIME_UNSET): MediaMetadataCompat {
        val metadata = item.mediaMetadata
        val builder = MediaMetadataCompat.Builder()
        metadata.title?.let { builder.putText(MediaMetadataCompat.METADATA_KEY_TITLE, it.toString()) }
        metadata.artist?.let { builder.putText(MediaMetadataCompat.METADATA_KEY_ARTIST, it.toString()) }
        metadata.albumTitle?.let { builder.putText(MediaMetadataCompat.METADATA_KEY_ALBUM, it.toString()) }
        metadata.albumArtist?.let {
            builder.putText(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, it.toString())
        }
        metadata.artworkUri?.let {
            builder.putText(MediaMetadataCompat.METADATA_KEY_ART_URI, it.toString())
            builder.putText(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it.toString())
        }
        metadata.subtitle?.let {
            builder.putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, it.toString())
        }
        if (durationMs != TIME_UNSET)
            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
        metadata.extras?.let { builder.setCustomExtra(it) }
        return builder.build()
    }

    fun toQueueItem(item: MediaItem, position: Int): MediaSessionCompat.QueueItem =
        MediaSessionCompat.QueueItem(toCompatMetadata(item).description, position.toLong())
}

private typealias Bundle = android.os.Bundle
