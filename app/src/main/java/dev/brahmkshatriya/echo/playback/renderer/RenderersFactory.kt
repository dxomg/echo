package dev.brahmkshatriya.echo.playback.renderer

import android.content.Context
import com.google.android.exoplayer2.audio.SonicAudioProcessor
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.audio.DefaultAudioSink
import com.google.android.exoplayer2.audio.SilenceSkippingAudioProcessor

class RenderersFactory(
    context: Context
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
        enableOffload: Boolean
    ) = run {
        val silenceSkippingAudioProcessor = SilenceSkippingAudioProcessor(
            2_000_000L,
            20_000L,
            256.toShort(),
        )

        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setOffloadMode(
                if (enableOffload) DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED
                else DefaultAudioSink.OFFLOAD_MODE_DISABLED
            )
            .setAudioProcessorChain(
                DefaultAudioSink.DefaultAudioProcessorChain(
                    emptyArray(), silenceSkippingAudioProcessor, SonicAudioProcessor()
                )
            )
            .build()
    }
}
