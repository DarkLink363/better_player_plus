import 'package:better_player_plus/src/configuration/better_player_buffering_configuration.dart';
import 'package:better_player_plus/src/video_player/method_channel_video_player.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final TestDefaultBinaryMessenger messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  const MethodChannel channel = MethodChannel('better_player_channel');
  final List<MethodCall> calls = <MethodCall>[];

  setUp(() {
    calls.clear();
    messenger.setMockMethodCallHandler(channel, (MethodCall call) async {
      calls.add(call);
      return <String, dynamic>{'textureId': 1};
    });
  });

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('create keeps decoder reuse on a resolution change by default', () async {
    await MethodChannelVideoPlayer().create(bufferingConfiguration: const BetterPlayerBufferingConfiguration());

    expect((calls.single.arguments as Map<Object?, Object?>)['reinitializeDecoderOnResolutionChange'], isFalse);
  });

  test('create asks for a new decoder on a resolution change when configured', () async {
    await MethodChannelVideoPlayer().create(
      bufferingConfiguration: const BetterPlayerBufferingConfiguration(reinitializeDecoderOnResolutionChange: true),
    );

    expect((calls.single.arguments as Map<Object?, Object?>)['reinitializeDecoderOnResolutionChange'], isTrue);
  });
}
