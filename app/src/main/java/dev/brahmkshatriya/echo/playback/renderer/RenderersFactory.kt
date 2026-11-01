package dev.brahmkshatriya.echo.playback.renderer

import android.content.Context
import com.google.android.exoplayer2.audio.SonicAudioProcessor
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.audio.DefaultAudioSink
import com.google.android.exoplayer2.audio.SilenceSkippingAudioProcessor
import com.google.android.exoplayer2.util.UnstableApi

@androidx.annotation.OptIn(UnstableApi::class)
class RenderersFactory(
    context: Context
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ) = run {
        val silenceSkippingAudioProcessor = SilenceSkippingAudioProcessor(
            2_000_000,
            (20_000 / 2_000_000).toFloat(),
            2_000_000,
            0,
            256,
        )

        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessorChain(
                DefaultAudioSink.DefaultAudioProcessorChain(
                    emptyArray(), silenceSkippingAudioProcessor, SonicAudioProcessor()
                )
            )
            .build()
    }
}