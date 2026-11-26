package uz.shs.better_player_plus

import android.content.Context
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * Builds a video renderer that starts a new decoder whenever the resolution changes,
 * instead of reconfiguring the one it has.
 *
 * Some decoders keep reporting the old output size after such a reconfiguration, both
 * on an adaptive switch and when the next source has another resolution. The picture is
 * then shown cropped to the old size and zoomed.
 */
@UnstableApi
internal class ResolutionSafeRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        out.add(
            object : MediaCodecVideoRenderer(
                context,
                codecAdapterFactory,
                mediaCodecSelector,
                allowedVideoJoiningTimeMs,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
            ) {
                override fun canReuseCodec(
                    codecInfo: MediaCodecInfo,
                    oldFormat: Format,
                    newFormat: Format,
                    isNewFormatFromDifferentStream: Boolean
                ): DecoderReuseEvaluation {
                    val evaluation = super.canReuseCodec(
                        codecInfo, oldFormat, newFormat, isNewFormatFromDifferentStream
                    )
                    val resolutionChanged =
                        oldFormat.width != newFormat.width || oldFormat.height != newFormat.height
                    if (!resolutionChanged || evaluation.result == DecoderReuseEvaluation.REUSE_RESULT_NO) {
                        return evaluation
                    }
                    return DecoderReuseEvaluation(
                        codecInfo.name,
                        oldFormat,
                        newFormat,
                        DecoderReuseEvaluation.REUSE_RESULT_NO,
                        evaluation.discardReasons or
                                DecoderReuseEvaluation.DISCARD_REASON_VIDEO_RESOLUTION_CHANGED
                    )
                }
            }
        )
    }
}
