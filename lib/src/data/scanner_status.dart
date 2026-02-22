import 'package:external_app_scanner/external_app_scanner.dart';

class ScannerStatus {
  final Type type;
  final Code code;

  /// Опциональная модель устройства.
  /// Заполнена только при событиях подключения/отключения [Code.DEVICE_CONNECTED] или [Code.DEVICE_DISCONNECTED].
  final BluetoothDevice? device;

  /// Приватный конструктор. Экземпляр только через фабричный метод [BleStatusMessage.fromDynamic]
  const ScannerStatus._({required this.type, required this.code, required this.device});

  factory ScannerStatus.fromDynamic(dynamic data) {
    final map = data is Map ? data : {};
    final deviceMap = map['device'];

    return ScannerStatus._(
      type: Type.fromString(map['type'] ?? ''),
      code: Code.fromString(map['code'] ?? ''),
      device: deviceMap is Map ? BluetoothDevice.fromMap(deviceMap) : null,
    );
  }

  @override
  String toString() => 'ScannerStatus{type: $type, code: $code, device: $device}';

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is ScannerStatus && type == other.type && code == other.code && device == other.device;
  }

  @override
  int get hashCode => type.hashCode ^ code.hashCode ^ device.hashCode;
}
