import 'package:external_app_scanner/src/data/data.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'external_app_scanner_method_channel.dart';

abstract class ExternalAppScannerPlatform extends PlatformInterface {
  ExternalAppScannerPlatform() : super(token: _token);

  static final Object _token = Object();

  static ExternalAppScannerPlatform _instance = ExternalAppScannerMethodChannel();

  static ExternalAppScannerPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [ExternalAppScannerPlatform] when
  /// they register themselves.
  static set instance(ExternalAppScannerPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Поток данных сканирования.
  Stream<String> get dataStream;

  /// Поток событий состояния сканера (ошибки, статусы подключения, готовность адаптера).
  Stream<ScannerStatus> get statusStream;

  Future<void> init();

  Future<void> dispose();

  /// Запрос на активацию Bluetooth на устройстве.
  ///
  /// Возвращает:
  /// * [true] - если Bluetooth уже включен, либо был включен через системный диалог.
  /// * [false] - если пользователь отказался включить Bluetooth
  /// * Исключения [ScannerException], если адаптер недоступен или возникла системная ошибка.
  Future<bool> enableBt();

  /// Запускает сервис и начинает трансляцию присутствия (Advertising).
  /// После этого устройство можно будет найти в другом приложении.
  /// * Исключения [ScannerException], если Bluetooth выключен или Advertise не поддерживается.
  Future<void> start();

  /// Останавливает BLE-сервис и прекращает рассылку. Все активные соединения со сканерами будут разорваны.
  /// * Исключения [ScannerException].
  Future<void> stop();

  /// Позволяет мгновенно получить текущее состояние сервиса в формате [ServiceStatus]
  /// (например, запущен ли сервер или идет ли рассылка) без ожидания события в [statusStream]
  /// * Исключения [ScannerException].
  Future<ScannerStatus> getStatus();
}
