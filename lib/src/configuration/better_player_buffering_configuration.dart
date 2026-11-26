///Configuration class used to setup better buffering experience or setup custom
///load settings. Currently used only in Android.
class BetterPlayerBufferingConfiguration {
  const new({
    this.minBufferMs = defaultMinBufferMs,
    this.maxBufferMs = defaultMaxBufferMs,
    this.bufferForPlaybackMs = defaultBufferForPlaybackMs,
    this.bufferForPlaybackAfterRebufferMs = defaultBufferForPlaybackAfterRebufferMs,
    this.reinitializeDecoderOnResolutionChange = false,
  });

  ///Constants values are from the offical exoplayer documentation
  ///https://exoplayer.dev/doc/reference/constant-values.html#com.google.android.exoplayer2.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
  static const defaultMinBufferMs = 25000;
  static const defaultMaxBufferMs = 6553600;
  static const defaultBufferForPlaybackMs = 3000;
  static const defaultBufferForPlaybackAfterRebufferMs = 6000;

  /// The default minimum duration of media that the player will attempt to
  /// ensure is buffered at all times, in milliseconds.
  final int minBufferMs;

  /// The default maximum duration of media that the player will attempt to
  /// buffer, in milliseconds.
  final int maxBufferMs;

  /// The default duration of media that must be buffered for playback to start
  /// or resume following a user action such as a seek, in milliseconds.
  final int bufferForPlaybackMs;

  /// The default duration of media that must be buffered for playback to resume
  /// after a rebuffer, in milliseconds. A rebuffer is defined to be caused by
  /// buffer depletion rather than a user action.
  final int bufferForPlaybackAfterRebufferMs;

  /// Starts a new video decoder whenever the resolution changes, instead of
  /// reconfiguring the one in use. Android only.
  ///
  /// Some decoders keep reporting the old size after being reconfigured, on an
  /// adaptive quality switch and when the next source played by the same
  /// controller has another resolution. The picture is then shown cropped and
  /// zoomed. The cost is a brief pause in the picture at each such change.
  ///
  /// Like the buffer durations, it is read once, when the controller plays
  /// its first data source.
  final bool reinitializeDecoderOnResolutionChange;
}
