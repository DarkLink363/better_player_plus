import 'package:better_player_plus/src/video_player/method_channel_video_player.dart';
import 'package:better_player_plus/src/video_player/video_player_platform_interface.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final TestDefaultBinaryMessenger messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  const MethodChannel channel = MethodChannel('better_player_channel');

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  group('playback diagnostics', () {
    test('a diagnostic event carries its type and data', () async {
      const String eventsChannelName = 'better_player_channel/videoEvents7';
      messenger.setMockMethodCallHandler(const MethodChannel(eventsChannelName), (MethodCall call) async {
        if (call.method == 'listen') {
          await messenger.handlePlatformMessage(
            eventsChannelName,
            const StandardMethodCodec().encodeSuccessEnvelope(<String, dynamic>{
              'event': 'diagnostic',
              'type': 'videoDecoderInitialized',
              'data': <String, dynamic>{'decoder': 'c2.exynos.h264.decoder', 'software': false},
            }),
            (ByteData? data) {},
          );
        }
        return null;
      });

      final VideoEvent event = await MethodChannelVideoPlayer().videoEventsFor(7).first;

      expect(event.eventType, VideoEventType.diagnostic);
      expect(event.diagnosticType, 'videoDecoderInitialized');
      expect(event.diagnosticData, <String, dynamic>{'decoder': 'c2.exynos.h264.decoder', 'software': false});
    });

    test('getDiagnostics returns what the platform reports', () async {
      messenger.setMockMethodCallHandler(channel, (MethodCall call) async {
        expect(call.method, 'getDiagnostics');
        expect(call.arguments, <String, dynamic>{'textureId': 3});
        return <String, dynamic>{'playbackState': 'buffering', 'bufferedPositionMs': 4200};
      });

      expect(await MethodChannelVideoPlayer().getDiagnostics(3), <String, dynamic>{
        'playbackState': 'buffering',
        'bufferedPositionMs': 4200,
      });
    });

    test('getDiagnostics is empty on a platform that does not implement it', () async {
      expect(await MethodChannelVideoPlayer().getDiagnostics(3), isEmpty);
    });
  });
}
