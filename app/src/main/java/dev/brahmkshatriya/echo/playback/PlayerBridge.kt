package dev.brahmkshatriya.echo.playback

import android.graphics.Bitmap
import android.os.Bundle
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Rating

/**
 * In-process replacement for the media3 MediaController command surface.
 *
 * The service is not a separate process, so the UI talks to the Player
 * directly. Custom commands (play item / queue / radio / like / sleep
 * timer / resume / image) historically went through the media session;
 * they now route onto the same string ids through this bridge, with the
 * implementation registered by the service when it comes up.
 */
object PlayerBridge {

    interface Handler {
        fun handleCustomCommand(player: Player, command: String, args: Bundle)
        fun handleRating(player: Player, rating: Rating)
        suspend fun loadImage(player: Player): Bitmap?
    }

    @Volatile
    private var handler: Handler? = null

    fun set(handler: Handler?) {
        this.handler = handler
    }

    fun Player.sendCustomCommand(command: String, args: Bundle = Bundle.EMPTY) {
        handler?.handleCustomCommand(this, command, args)
    }

    fun Player.setRating(rating: Rating) {
        handler?.handleRating(this, rating)
    }

    suspend fun Player.loadImage(): Bitmap? = handler?.loadImage(this)
}
