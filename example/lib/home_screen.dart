import 'dart:async';
import 'dart:developer';
import 'dart:io';

import 'package:external_app_scanner/external_app_scanner.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart' hide ServiceStatus;

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  StreamSubscription? _statusSub;
  StreamSubscription? _dataSub;

  String _lastData = '';
  Code? _code;
  ExtBluetoothDevice? _device;

  @override
  void dispose() {
    _statusSub?.cancel();
    _dataSub?.cancel();
    super.dispose();
  }

  Future<void> subscribe() async {
    try {
      _statusSub = ExternalAppScannerPlatform.instance.statusStream.listen((ExtAppScannerStatus status) {
        if (status.device != null) {
          _device = status.device;
        }
        if (status.code == Code.deviceDisconnected && mounted) {
          setState(() => _device = null);
          ScaffoldMessenger.of(context).clearSnackBars();
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Устройство отключено')));
          return;
        }
        setState(() {
          _code = status.code;
        });
      });

      _dataSub = ExternalAppScannerPlatform.instance.dataStream.listen((String data) {
        setState(() => _lastData = data);
      });
    } on ScannerException catch (e) {
      _showSnackBar(isSuccess: false, errorText: e.code.message);
    }
  }

  Future<void> unsubscribe() async {
    try {
      _statusSub?.cancel();
      _dataSub?.cancel();
    } on ScannerException catch (e) {
      _showSnackBar(isSuccess: false, errorText: e.code.message);
    }
  }

  Future<void> startServer() async {
    try {
      await ExternalAppScannerPlatform.instance.start();
      _showSnackBar(isSuccess: true, successText: 'Сервис запущен');
    } on ScannerException catch (e) {
      _showSnackBar(isSuccess: false, errorText: e.code.message);
    }
  }

  Future<void> stopServer() async {
    await ExternalAppScannerPlatform.instance.stop();
  }

  Future<void> enableBT() async {
    final bool isEnabled;
    try {
      isEnabled = await ExternalAppScannerPlatform.instance.enableBt();
      if (!mounted) return;
      _showSnackBar(isSuccess: isEnabled, successText: 'Bluetooth ON!', errorText: 'Bluetooth OFF!');
    } on ScannerException catch (e) {
      _showSnackBar(isSuccess: false, errorText: e.code.message);
    }
  }

  Future<void> requestPermissions() async {
    // 1. Динамически формируем список разрешений в зависимости от версии Android
    final List<Permission> permissions = [];

    if (Platform.isAndroid) {
      final int? sdkInt = await MethodChannel('external_app_scanner/methods').invokeMethod<int>('getAndroidVersion');
      log('sdkInt from MethodChannel: $sdkInt');
      if (sdkInt != null && sdkInt >= 31) {
        // Android 12+
        permissions.addAll([Permission.bluetoothAdvertise, Permission.bluetoothConnect]);
      } else {
        // Android 11 и ниже
        permissions.addAll([Permission.location]);
      }

      // Добавляем уведомления для Android 13+ (для Foreground Service)
      if (sdkInt != null && sdkInt >= 33) {
        permissions.add(Permission.notification);
      }
    }

    // 2. Запрашиваем всё разом
    final Map<Permission, PermissionStatus> statuses = await permissions.request();

    // 3. Проверяем результат
    bool allGranted = statuses.values.every((status) => status.isGranted);
    bool isPermanentlyDenied = statuses.values.any((status) => status.isPermanentlyDenied);

    if (allGranted) {
      _showSnackBar(isSuccess: true, successText: 'Permissions GRANTED!', errorText: 'Permissions NOT GRANTED!');
    } else {
      _showSnackBar(
        isSuccess: false,
        errorText: isPermanentlyDenied ? 'Permissions blocked. Open settings!' : 'Permissions NOT GRANTED!',
        successText: 'Permissions GRANTED!',
      );

      // 4. Если пользователь нажал "Больше не спрашивать", помогаем ему
      if (isPermanentlyDenied) {
        await openAppSettings();
      }
    }
  }

  Future<void> checkPermissions() async {
    final List<Permission> permissions = [Permission.bluetoothAdvertise, Permission.bluetoothConnect];

    bool hasPermissions = true;
    for (final Permission permission in permissions) {
      final status = await permission.status;
      if (!status.isGranted) {
        hasPermissions = false;
      }
    }

    _showSnackBar(
      isSuccess: hasPermissions,
      successText: 'Permissions GRANTED!',
      errorText: 'Permissions NOT GRANTED!',
    );
  }

  void _showSnackBar({required bool isSuccess, String successText = '', String errorText = ''}) {
    if (!mounted) return;
    final String text = isSuccess ? successText : errorText;
    ScaffoldMessenger.of(context).clearSnackBars();
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(text), backgroundColor: isSuccess ? Colors.green : Colors.red));
  }

  Future<void> getStatus() async {
    try {
      final ExtAppScannerStatus currentStatus = await ExternalAppScannerPlatform.instance.getStatus();
      setState(() {
        _device = currentStatus.device;
        _code = currentStatus.code;
      });
    } on ScannerException catch (e) {
      _showSnackBar(isSuccess: false, errorText: e.code.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Scanner Example')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: SingleChildScrollView(
          child: Column(
            children: [
              Text('Code: ${_code?.message}', style: const TextStyle(fontSize: 18)),
              if (_device != null) ...[
                const SizedBox(height: 32),
                Text('Device: ${_device?.name}', style: const TextStyle(fontSize: 18)),
                Text('Address: ${_device?.address}', style: const TextStyle(fontSize: 18)),
                Text('Type: ${_device?.type}', style: const TextStyle(fontSize: 18)),
                Text('Bond state: ${_device?.bondState}', style: const TextStyle(fontSize: 18)),
              ],
              const SizedBox(height: 32),
              Text('Last scan: $_lastData', style: const TextStyle(fontSize: 18)),
              const SizedBox(height: 32),
              Wrap(
                runSpacing: 16,
                spacing: 8,
                children: [
                  FilledButton(onPressed: startServer, child: const Text('Start')),
                  FilledButton(onPressed: stopServer, child: const Text('Stop')),
                  FilledButton(onPressed: enableBT, child: const Text('Enable BT')),
                  FilledButton(onPressed: checkPermissions, child: const Text('Check permissions')),
                  FilledButton(onPressed: requestPermissions, child: const Text('Request permissions')),
                  FilledButton(onPressed: getStatus, child: const Text('Get status')),
                  FilledButton(onPressed: subscribe, child: const Text('Subscribe')),
                  FilledButton(onPressed: unsubscribe, child: const Text('Unsubscribe')),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
