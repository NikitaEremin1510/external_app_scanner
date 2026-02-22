package by.belblank.echeck.external_app_scanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ExternalAppScannerService extends Service {
    private static final String CHANNEL_ID = "ExternalAppScanner";
    private static final int SERVICE_ID = 1;
    private static final String ACTION_STOP = "by.belblank.echeck.external_app_scanner.EXT_APP_SCANNER_STOP";

    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;

    public boolean isServiceRunning = false;
    private BluetoothDevice currentDevice;

    private final ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();

    @Override
    public void onCreate() {
        super.onCreate();
        this.btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (btManager != null) {
            btAdapter = btManager.getAdapter();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isTerminationAction(intent)) {
            stopCurrentService();
            return START_NOT_STICKY;
        }

        if (isServiceRunning) return START_STICKY;

        start();
        initializeGattServer();
        return START_STICKY;
    }

    private boolean isTerminationAction(Intent intent) {
        return intent != null && ACTION_STOP.equals(intent.getAction());
    }

    private void start() {
        createNotificationChannel();
        Notification notification = createNotification("Инициализация...");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            startForeground(SERVICE_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(SERVICE_ID, notification);
        }
        Logger.i("Service started.");
    }

    private void initializeGattServer() {
        if (btAdapter == null || !btAdapter.isEnabled()) return;
        openGattServer();
        startAdvertising();
        ScannerEvents.StatusBuilder.info(Constants.Codes.SERVICE_STARTED).send();
    }

    private void openGattServer() {
        gattServer = btManager.openGattServer(this, gattServerCallback);
        if (gattServer != null) {
            BluetoothGattService service = new BluetoothGattService(
                    Constants.SERVICE_UUID,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
            );

            BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                    Constants.CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
            );

            service.addCharacteristic(characteristic);
            gattServer.addService(service);
        } else {
            ScannerEvents.StatusBuilder.error(Constants.Codes.ERROR_GATT_INIT).send();
        }
    }

    private void startAdvertising() {
        if (btAdapter == null || !btAdapter.isEnabled()) return;

        advertiser = btAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) return;

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setConnectable(true)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTimeout(0) // 0 = без лимита по времени
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false) // Имя лучше в ScanResponse, чтобы влез UUID
                .addServiceUuid(new ParcelUuid(Constants.SERVICE_UUID))
                .build();

        AdvertiseData scanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build();

        try {
            advertiser.stopAdvertising(advertiseCallback);
            advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback);
        } catch (Exception e) {
            Logger.e("Failed to start advertising", e);
        }
    }

    // --- Callbacks ---

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            isServiceRunning = true;
            Logger.i("Advertising started. Settings: " + settingsInEffect.toString() + ".");
            updateNotification("Ожидание подключения...");
            ScannerEvents.StatusBuilder.info(Constants.Codes.ADVERTISING_STARTED).send();
        }

        @Override
        public void onStartFailure(int errorCode) {
            Logger.e("Advertising start failure. Error code: " + errorCode + ".", null);
            updateNotification("Ошибка при запуске трансляции...");
            ScannerEvents.StatusBuilder.error(Constants.Codes.ERROR_ADVERTISE_FAILED).send();
        }
    };

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            // Устройство подключено
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Logger.i("Device connected. Device name: " + device.getName() + " Device address: [" + device.getAddress() + "]");
                currentDevice = device;
                updateNotification("Устройство " + device.getName() + " подключено");
                ScannerEvents.StatusBuilder.info(Constants.Codes.DEVICE_CONNECTED)
                        .addDevice(createBluetoothDeviceMap(device))
                        .send();
            }
            // Устройство отключено
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                updateNotification("Устройство отключено");
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Logger.w("Device disconnected with error. Device name: " + device.getName() + " Device address: [" + device.getAddress() + "]. Error: " + status);
                } else {
                    Logger.i("Device disconnected.");
                }
                messageBuffer.reset();
                currentDevice = null;
                ScannerEvents.StatusBuilder.info(Constants.Codes.DEVICE_DISCONNECTED)
                        .addDevice(createBluetoothDeviceMap(device))
                        .send();

                // Перезапуск рекламы через секунду, так как некоторые устройства останавливают ее при отключении
                new Handler(Looper.getMainLooper()).postDelayed(ExternalAppScannerService.this::startAdvertising, 1000);
            }
        }


        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {

            Logger.i("Characteristic write request. Device name: " + device.getName() + " Device address: [" + device.getAddress() + "]");
            Logger.i("Request id: " + requestId + ".");
            Logger.i("Characteristic uuid: " + characteristic.getUuid().toString() + ".");
            Logger.i("Characteristic preparedWrite: " + preparedWrite + ".");
            Logger.i("Characteristic responseNeeded: " + responseNeeded + ".");
            Logger.i("Characteristic offset: " + offset + ".");
            Logger.i("Characteristic value: " + new String(value, StandardCharsets.UTF_8));
            // Если это начало нового сообщения (offset 0) и мы не в режиме preparedWrite,
            // то старое сообщение (если было) считаем законченным или просто сбрасываем.
            if (offset == 0 && !preparedWrite) {
                messageBuffer.reset();
            }

            try {
                messageBuffer.write(value);
            } catch (Exception e) {
                Logger.e("Buffer write error", e);
            }

            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }

            // Если это обычная запись (не preparedWrite), то сразу отправляем,
            // так как это может быть одиночный пакет.
            // Если же клиент шлет чанками через обычный write, он должен сам знать, когда конец.
            if (!preparedWrite) {
                flushBuffer();
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            if (execute) {
                flushBuffer();
            } else {
                messageBuffer.reset();
            }
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
        }
    };

    private void flushBuffer() {
        if (messageBuffer.size() > 0) {
            String message = new String(messageBuffer.toByteArray(), StandardCharsets.UTF_8);
            new ScannerEvents.Data(message).send();
            messageBuffer.reset();
        }
    }

    // --- Notification Helpers ---

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "ExternalAppScanner", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @NonNull
    private Notification createNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Внешний сканер")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(createOpenAppIntent())
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", createStopServiceIntent())
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(SERVICE_ID, createNotification(text));
    }

    // --- Intents Helpers ---
    private PendingIntent createStopServiceIntent() {
        Intent intent = new Intent(this, ExternalAppScannerService.class).setAction(ACTION_STOP);
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                | PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getService(this, 0, intent, flags);
    }

    private PendingIntent createOpenAppIntent() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getActivity(this, 0, intent, flags);
    }

    public Map<String, Object> getCurrentStatus() {
        String code = Constants.Codes.SERVICE_STOPPED;
        if (isServiceRunning) code = Constants.Codes.SERVICE_STARTED;
        if (currentDevice != null) code = Constants.Codes.DEVICE_CONNECTED;

        return Utils.buildStatusMap(code, Constants.StatusType.INFO, currentDevice != null ? createBluetoothDeviceMap(currentDevice) : null);
    }


    // --- BINDER ---
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends android.os.Binder {
        ExternalAppScannerService getService() {
            return ExternalAppScannerService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        stopCurrentService();
        isServiceRunning = false;
        ScannerEvents.StatusBuilder.info(Constants.Codes.SERVICE_STOPPED).send();
        super.onDestroy();
    }

    /**
     * Метод для полной остановки логики сканера (BLE, уведомление, флаги)
     */
    public void stopCurrentService() {
        Logger.i("Stopping service.");

        // Останавливаем BLE
        try {
            if (advertiser != null && btAdapter != null && btAdapter.isEnabled()) {
                advertiser.stopAdvertising(advertiseCallback);
            }
        } catch (Exception e) {
            Logger.e("Error stopping advertising", e);
        }

        if (gattServer != null) {
            gattServer.clearServices();
            gattServer.close();
            gattServer = null;
        }

        // Убираем уведомление из шторки
        stopForeground(true);

        isServiceRunning = false;
        currentDevice = null;
        messageBuffer.reset();

        ScannerEvents.StatusBuilder.info(Constants.Codes.SERVICE_STOPPED).send();

        stopSelf();
    }

    @NonNull
    private Map<String, Object> createBluetoothDeviceMap(@NonNull BluetoothDevice device) {
        Map<String, Object> deviceMap = new HashMap<>();
        deviceMap.put("name", device.getName());
        deviceMap.put("address", device.getAddress());
        deviceMap.put("type", device.getType());
        deviceMap.put("bondState", device.getBondState());
        return deviceMap;
    }
}