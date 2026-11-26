package uz.shs.better_player_plus

import android.content.Context
import android.media.MediaCodec
import android.os.SystemClock
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import io.flutter.view.TextureRegistry.SurfaceProducer
import java.io.IOException

/**
 * Collects what the player knows about decoders, the surface and loading, so a
 * slow or failed playback can be told apart: network, decoder or surface.
 *
 * Reports "diagnostic" events through [emit] and builds the details map that
 * goes with a playback error.
 */
@UnstableApi
internal class PlaybackDiagnostics(
    context: Context,
    private val surfaceProducer: SurfaceProducer,
    private val emit: (Map<String, Any?>) -> Unit,
) : AnalyticsListener {
    private val appContext: Context = context.applicationContext
    private var prepareStartedAtMs = 0L
    private var msToFirstFrame: Long? = null
    private var videoDecoder: String? = null
    private var audioDecoder: String? = null
    private var videoDecoderInitCount = 0
    private var droppedVideoFrames = 0
    private var loadErrorCount = 0
    private var lastLoadError: String? = null

    /** Call right before `prepare()` for a new media source. */
    fun onPrepare() {
        prepareStartedAtMs = SystemClock.elapsedRealtime()
        msToFirstFrame = null
        videoDecoderInitCount = 0
        droppedVideoFrames = 0
        loadErrorCount = 0
        lastLoadError = null
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
        videoDecoder = decoderName
        videoDecoderInitCount++
        send(
            "videoDecoderInitialized", mapOf(
                "decoder" to decoderName,
                "software" to isSoftwareDecoder(decoderName),
                "initDurationMs" to initializationDurationMs,
                "initCount" to videoDecoderInitCount,
                "msSincePrepare" to msSincePrepare()
            )
        )
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
        audioDecoder = decoderName
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?
    ) {
        send(
            "videoFormatChanged", mapOf(
                "format" to describe(format),
                "decoderReuse" to decoderReuseEvaluation?.let { reuseResultName(it.result) },
                "discardReasons" to decoderReuseEvaluation?.discardReasons,
                "msSincePrepare" to msSincePrepare()
            )
        )
    }

    override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
        send(
            "videoSizeChanged", mapOf(
                "width" to videoSize.width,
                "height" to videoSize.height,
                "surfaceWidth" to surfaceProducer.width,
                "surfaceHeight" to surfaceProducer.height
            )
        )
    }

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long
    ) {
        if (msToFirstFrame == null) {
            msToFirstFrame = msSincePrepare()
        }
        send("renderedFirstFrame", mapOf("msSincePrepare" to msSincePrepare()))
    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long
    ) {
        droppedVideoFrames += droppedFrames
    }

    override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, videoCodecError: Exception) {
        send("videoCodecError", mapOf("causes" to causeChain(videoCodecError)) + codecDetails(videoCodecError))
    }

    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean
    ) {
        loadErrorCount++
        lastLoadError = describe(error)
        send(
            "loadError", mapOf(
                "dataType" to mediaLoadData.dataType,
                "trackType" to mediaLoadData.trackType,
                "host" to loadEventInfo.uri.host,
                "bytesLoaded" to loadEventInfo.bytesLoaded,
                "loadDurationMs" to loadEventInfo.loadDurationMs,
                "wasCanceled" to wasCanceled,
                "count" to loadErrorCount,
                "causes" to causeChain(error)
            )
        )
    }

    /** Current state of the player, for a playback that hangs without an error. */
    fun snapshot(player: ExoPlayer?): Map<String, Any?> {
        val surface = runCatching { surfaceProducer.surface }.getOrNull()
        return mapOf(
            "playbackState" to playbackStateName(player?.playbackState),
            "playWhenReady" to player?.playWhenReady,
            "isPlaying" to player?.isPlaying,
            "isLoading" to player?.isLoading,
            "positionMs" to player?.currentPosition,
            "bufferedPositionMs" to player?.bufferedPosition,
            "totalBufferedMs" to player?.totalBufferedDuration,
            "msSincePrepare" to msSincePrepare(),
            "msToFirstFrame" to msToFirstFrame,
            "videoDecoder" to videoDecoder,
            "videoDecoderSoftware" to videoDecoder?.let { isSoftwareDecoder(it) },
            "videoDecoderInitCount" to videoDecoderInitCount,
            "audioDecoder" to audioDecoder,
            "videoFormat" to player?.videoFormat?.let { describe(it) },
            "videoWidth" to player?.videoSize?.width,
            "videoHeight" to player?.videoSize?.height,
            "droppedVideoFrames" to droppedVideoFrames,
            "loadErrorCount" to loadErrorCount,
            "lastLoadError" to lastLoadError,
            "bandwidthEstimate" to runCatching {
                DefaultBandwidthMeter.getSingletonInstance(appContext).bitrateEstimate
            }.getOrNull(),
            "surfaceValid" to surface?.isValid,
            "surfaceWidth" to surfaceProducer.width,
            "surfaceHeight" to surfaceProducer.height,
            "playerError" to player?.playerError?.errorCodeName
        )
    }

    /** Everything the error carries that its `toString()` drops. */
    fun errorDetails(error: PlaybackException, player: ExoPlayer?): Map<String, Any?> {
        val details = HashMap<String, Any?>()
        details["errorCode"] = error.errorCode
        details["errorCodeName"] = error.errorCodeName
        details["causes"] = causeChain(error)
        if (error is ExoPlaybackException) {
            details["type"] = when (error.type) {
                ExoPlaybackException.TYPE_SOURCE -> "source"
                ExoPlaybackException.TYPE_RENDERER -> "renderer"
                ExoPlaybackException.TYPE_UNEXPECTED -> "unexpected"
                ExoPlaybackException.TYPE_REMOTE -> "remote"
                else -> "unknown"
            }
            if (error.type == ExoPlaybackException.TYPE_RENDERER) {
                details["rendererName"] = error.rendererName
                details["rendererFormat"] = error.rendererFormat?.let { describe(it) }
                details["rendererFormatSupport"] = error.rendererFormatSupport
            }
        }
        details.putAll(codecDetails(error))
        details["state"] = snapshot(player)
        return details
    }

    private fun send(type: String, data: Map<String, Any?>) {
        emit(mapOf("event" to "diagnostic", "type" to type, "data" to data))
    }

    private fun msSincePrepare(): Long? =
        if (prepareStartedAtMs == 0L) null else SystemClock.elapsedRealtime() - prepareStartedAtMs

    private fun codecDetails(error: Throwable): Map<String, Any?> {
        val details = HashMap<String, Any?>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSES) {
            when (current) {
                is MediaCodecDecoderException -> {
                    details["decoder"] = current.codecInfo?.name
                    details["decoderDiagnosticInfo"] = current.diagnosticInfo
                }

                is MediaCodecRenderer.DecoderInitializationException -> {
                    details["decoder"] = current.codecInfo?.name
                    details["decoderMimeType"] = current.mimeType
                    details["decoderDiagnosticInfo"] = current.diagnosticInfo
                }

                is MediaCodec.CodecException -> {
                    details["codecErrorCode"] = current.errorCode
                    details["codecDiagnosticInfo"] = current.diagnosticInfo
                    details["codecTransient"] = current.isTransient
                    details["codecRecoverable"] = current.isRecoverable
                }
            }
            current = current.cause
            depth++
        }
        return details
    }

    private fun causeChain(error: Throwable): List<String> {
        val causes = ArrayList<String>()
        var current: Throwable? = error
        while (current != null && causes.size < MAX_CAUSES) {
            causes.add(describe(current))
            current = current.cause
        }
        return causes
    }

    private fun describe(error: Throwable): String =
        "${error.javaClass.name}: ${error.message}".take(MAX_MESSAGE_LENGTH)

    private fun describe(format: Format): Map<String, Any?> = mapOf(
        "width" to format.width,
        "height" to format.height,
        "bitrate" to format.bitrate,
        "codecs" to format.codecs,
        "mimeType" to format.sampleMimeType,
        "frameRate" to format.frameRate.toDouble()
    )

    private fun isSoftwareDecoder(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("c2.android.") || lower.startsWith("omx.google.") ||
                lower.startsWith("c2.google.") || lower.contains(".sw.")
    }

    private fun playbackStateName(state: Int?): String? = when (state) {
        null -> null
        Player.STATE_IDLE -> "idle"
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        else -> state.toString()
    }

    private fun reuseResultName(result: Int): String = when (result) {
        DecoderReuseEvaluation.REUSE_RESULT_NO -> "no"
        DecoderReuseEvaluation.REUSE_RESULT_YES_WITH_FLUSH -> "flush"
        DecoderReuseEvaluation.REUSE_RESULT_YES_WITH_RECONFIGURATION -> "reconfiguration"
        DecoderReuseEvaluation.REUSE_RESULT_YES_WITHOUT_RECONFIGURATION -> "seamless"
        else -> result.toString()
    }

    private companion object {
        const val MAX_CAUSES = 8
        const val MAX_MESSAGE_LENGTH = 300
    }
}
