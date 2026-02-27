import 'package:external_app_scanner/src/data/data.dart';
import 'package:flutter/services.dart';

import 'external_app_scanner_platform_interface.dart';

/// An implementation of [ExternalAppScannerPlatform] that uses method channels.
class ExternalAppScannerMethodChannel extends ExternalAppScannerPlatform {
  /// The method channel used to interact with the native platform.
  static const _methodChannel = MethodChannel('external_app_scanner/methods');

  static const _dataEventChannel = EventChannel("external_app_scanner/data");
  static const _statusEventChannel = EventChannel("external_app_scanner/status");

  ExternalAppScannerMethodChannel();

  @override
  Stream<String> get dataStream => _dataEventChannel.receiveBroadcastStream().cast<String>();

  @override
  Stream<ScannerStatus> get statusStream => _statusEventChannel.receiveBroadcastStream().map(ScannerStatus.fromDynamic);

  @override
  Future<bool> enableBt() async {
    try {
      return await _methodChannel.invokeMethod<bool>('enableBT') ?? false;
    } on PlatformException catch (e) {
      throw _handlePlatformException(e);
    }
  }

  @override
  Future<void> start() async {
    try {
      await _methodChannel.invokeMethod<bool>('start');
    } on PlatformException catch (e) {
      throw _handlePlatformException(e);
    }
  }

  @override
  Future<void> stop() async {
    try {
      await _methodChannel.invokeMethod<bool>('stop');
    } on PlatformException catch (e) {
      throw _handlePlatformException(e);
    }
  }

  @override
  Future<ScannerStatus> getStatus() async {
    try {
      final result = await _methodChannel.invokeMethod('getStatus');
      return ScannerStatus.fromDynamic(result);
    } on PlatformException catch (e) {
      throw _handlePlatformException(e);
    }
  }

  ScannerException _handlePlatformException(PlatformException e) {
    return ScannerException(code: Code.fromString(e.code), errorMessage: e.message, errorDetails: e.details);
  }
}
