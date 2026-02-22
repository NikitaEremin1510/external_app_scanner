enum Code {
  // Инфо
  bluetoothEnabled('BT_ENABLED'),
  bluetoothDisabled('BT_DISABLED'),
  serviceStarted('SERVICE_STARTED'),
  serviceStopped('SERVICE_STOPPED'),
  advertisingStarted('ADVERTISING_STARTED'),
  deviceConnected('DEVICE_CONNECTED'),
  deviceDisconnected('DEVICE_DISCONNECTED'),

  // Ошибки
  errorGattInit('ERROR_GATT_INIT'),
  errorAdvertiseFailed('ERROR_ADVERTISE_FAILED'),
  permissionNotGranted('PERMISSION_NOT_GRANTED'),
  advertiseNotSupported('ADVERTISE_NOT_SUPPORTED'),
  serviceStartFailed('SERVICE_START_FAILED'),
  intentFailed('INTENT_FAILED'),
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
    Code.serviceStarted => 'Сервер успешно запущен',
    Code.serviceStopped => 'Сервер успешно остановлен',
    Code.advertisingStarted => 'Рассылка сигнала активна',
    Code.deviceConnected => 'Устройство успешно подключено',
    Code.deviceDisconnected => 'Устройство отключено',

    Code.errorGattInit => 'Ошибка инициализации GATT-сервера',
    Code.errorAdvertiseFailed => 'Не удалось запустить рассылку',
    Code.permissionNotGranted => 'Разрешения для работы Bluetooth не были предоставлены',
    Code.advertiseNotSupported => 'Устройство не поддерживает режим Bluetooth трансляции',
    Code.serviceStartFailed => 'Не удалось запустить фоновую службу',
    Code.intentFailed => 'Система не может выполнить действие',
    Code.alreadyInProgress => 'Операция уже выполняется',
    Code.noActivity => 'Ошибка контекста: приложение неактивно',
    Code.unknownStatus => 'Неизвестный статус',
  };
}
