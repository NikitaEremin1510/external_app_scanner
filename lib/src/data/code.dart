enum Code {
  // Инфо
  bluetoothEnabled('BT_ENABLED'),
  bluetoothDisabled('BT_DISABLED'),
  serviceStarted('SERVICE_STARTED'),
  serviceStopped('SERVICE_STOPPED'),
  deviceConnected('DEVICE_CONNECTED'),
  deviceDisconnected('DEVICE_DISCONNECTED'),

  // Ошибки
  errorGattInit('GATT_INIT_FAILED'),
  errorAdvertiseFailed('ADVERTISE_FAILED'),
  permissionNotGranted('PERMISSION_NOT_GRANTED'),
  advertiseNotSupported('ADVERTISE_NOT_SUPPORTED'),
  alreadyInProgress('ALREADY_IN_PROGRESS'),
  noActivity('NO_ACTIVITY'),

  unknownStatus('UNKNOWN_STATUS');

  final String value;

  const Code(this.value);

  static final Map<String, Code> _map = {for (var code in Code.values) code.value: code};

  static Code fromString(String? code) {
    return _map[code?.toUpperCase()] ?? Code.unknownStatus;
  }

  String get message => switch (this) {
    Code.bluetoothEnabled => 'Bluetooth включен',
    Code.bluetoothDisabled => 'Bluetooth выключен',
    Code.serviceStarted => 'Сервис запущен',
    Code.serviceStopped => 'Сервис остановлен',
    Code.deviceConnected => 'Устройство успешно подключено',
    Code.deviceDisconnected => 'Устройство отключено',

    Code.errorGattInit => 'Ошибка инициализации GATT-сервера',
    Code.errorAdvertiseFailed => 'Не удалось запустить рассылку',
    Code.permissionNotGranted => 'Разрешения для работы Bluetooth не были предоставлены',
    Code.advertiseNotSupported => 'Устройство не поддерживает режим Bluetooth трансляции',
    Code.alreadyInProgress => 'Операция уже выполняется',
    Code.noActivity => 'Ошибка контекста: приложение неактивно',
    Code.unknownStatus => 'Неизвестный статус',
  };
}
