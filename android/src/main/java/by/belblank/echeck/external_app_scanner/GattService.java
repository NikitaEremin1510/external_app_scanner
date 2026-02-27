package by.belblank.echeck.external_app_scanner;

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
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GattService {

    private final Context context;
    private final StreamHandlerImpl statusStreamHandler;
    private final StreamHandlerImpl dataStreamHandler;
    private final BluetoothManager bluetoothManager;
    private final BluetoothLeAdvertiser advertiser;
    private final ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();

    private BluetoothGattServer gattServer;
    private BluetoothDevice currentDevice;
    private boolean isRunning;

    private final BluetoothStateReceiver btStateReceiver = new BluetoothStateReceiver(this::stopServer);

    public GattService(@NonNull Context context, StreamHandlerImpl statusStreamHandler, StreamHandlerImpl dataStreamHandler) {
        this.context = context;
        this.statusStreamHandler = statusStreamHandler;
        this.dataStreamHandler = dataStreamHandler;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.advertiser = bluetoothManager.getAdapter().getBluetoothLeAdvertiser();
    }

    public Map<String, Object> getCurrentStatus() {
        String code = Constants.Codes.SERVICE_STOPPED;
        Map<String, Object> deviceMap = null;
        if (isRunning) {
            code = Constants.Codes.SERVICE_STARTED;
        }
        if (currentDevice != null) {
            code = Constants.Codes.DEVICE_CONNECTED;
            deviceMap = createBluetoothDeviceMap(currentDevice);
        }
        return Utils.buildStatusMap(code, Constants.StatusType.INFO, deviceMap);
    }

    public void startServer() {
        if (isRunning) return;
        registerBroadcastReceiver();
        startGattServer();
        if (currentDevice == null) {
            startAdvertising();
        }
        isRunning = true;
    }

    private void registerBroadcastReceiver() {
        if (context == null) return;
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(btStateReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(btStateReceiver, filter);
        }
        Logger.d("Broadcast receiver registered.");
    }

    private void unregisterBroadcastReceiver() {
        try {
            context.unregisterReceiver(btStateReceiver);
        } catch (Exception ignored) {
        }
        Logger.i("Broadcast receiver unregistered.");
    }

    public void stopServer() {
        stopAdvertising();
        stopGattServer();
        isRunning = false;
        unregisterBroadcastReceiver();
        statusStreamHandler.send(Utils.buildStatusMap(Constants.Codes.SERVICE_STOPPED, Constants.StatusType.INFO, null));
    }

    private void startAdvertising() {
        if (advertiser == null) return;

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setConnectable(true)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTimeout(0)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false) // Имя лучше в ScanResponse, чтобы влез UUID
                .addServiceUuid(new ParcelUuid(Constants.SERVICE_UUID))
                .build();

        AdvertiseData scanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build();

        advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback);
    }

    private void stopAdvertising() {
        if (advertiser != null) {
            advertiser.stopAdvertising(advertiseCallback);
            Logger.i("Advertising stopped.");
        }
    }

    private void startGattServer() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        Logger.i("Gatt Server started.");
        if (gattServer == null) {
            Logger.e("CRITICAL: openGattServer is NULL!", null);
            return;
        }

        BluetoothGattService service = getBluetoothGattService();
        gattServer.addService(service);
    }

    private void stopGattServer() {
        if (gattServer != null) {
            if (currentDevice != null) {
                gattServer.cancelConnection(currentDevice);
            }
            gattServer.clearServices();
            gattServer.close();
            gattServer = null;
            Logger.i("Gatt server closed.");
        }
    }

    private void flushBuffer() {
        if (messageBuffer.size() > 0) {
            String message = new String(messageBuffer.toByteArray(), StandardCharsets.UTF_8).trim();
            dataStreamHandler.send(message);
            messageBuffer.reset();
        }
    }


    // --- CALLBACKS ---
    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(@NonNull AdvertiseSettings settingsInEffect) {
            Logger.i("Advertising started. Settings: " + settingsInEffect + ".");
            if (currentDevice == null) {
                statusStreamHandler.send(Utils.buildStatusMap(Constants.Codes.SERVICE_STARTED, Constants.StatusType.INFO, null));
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            Logger.w("Advertising start failure. Error code: " + errorCode + ".");
            statusStreamHandler.send(Utils.buildStatusMap(Constants.Codes.ADVERTISE_FAILED, Constants.StatusType.ERROR, null));
        }
    };

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            // Устройство подключено
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Logger.i("Device connected. Device name: " + device.getName() + " Device address: [" + device.getAddress() + "]");
                currentDevice = device;
                statusStreamHandler.send(Utils.buildStatusMap(Constants.Codes.DEVICE_CONNECTED, Constants.StatusType.INFO, createBluetoothDeviceMap(device)));
                if (isRunning) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> stopAdvertising(), 1000L);
                }
            }
            // Устройство отключено
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Logger.i("Device disconnected. Status: " + status);
                messageBuffer.reset();
                currentDevice = null;
                statusStreamHandler.send(Utils.buildStatusMap(Constants.Codes.DEVICE_DISCONNECTED, Constants.StatusType.INFO, null));
                new Handler(Looper.getMainLooper()).postDelayed(() -> startAdvertising(), 500L);
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

            Logger.i(String.format(Locale.ROOT,
                    "BLE Write Request | Device: %s [%s] | ID: %d | UUID: %s | PrepWrite: %b | RespNeed: %b | Offset: %d | Value: '%s'",
                    device.getName(),
                    device.getAddress(),
                    requestId,
                    characteristic.getUuid().toString(),
                    preparedWrite,
                    responseNeeded,
                    offset,
                    new String(value, StandardCharsets.UTF_8)
            ));
            if (preparedWrite) {
                // Сценарий 1: Надежная запись (Reliable Write / Long Write)
                // Данные только накапливаются. Отправка будет в onExecuteWrite.
                try {
                    // Если это начало (offset 0), на всякий случай чистим буфер
                    if (offset == 0) messageBuffer.reset();
                    messageBuffer.write(value);
                } catch (IOException e) {
                    Logger.e("Buffer write error", e);
                }
            } else {
                // Сценарий 2: Обычная запись (Write Request / Write No Response)
                // Данные приходят целиком (или клиент сам дробит их на обычные Write).

                // Если это первый кусок или одиночный пакет
                if (offset == 0) {
                    messageBuffer.reset();
                }

                try {
                    messageBuffer.write(value);
                    flushBuffer();
                } catch (IOException e) {
                    Logger.e("Buffer write error", e);
                }
            }

            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            Logger.i(String.format(Locale.ROOT,
                    "BLE Execute Write | Device: %s [%s] | ID: %d | Execute: %b ",
                    (device.getName() != null ? device.getName() : "Unknown"),
                    device.getAddress(),
                    requestId,
                    execute)
            );
            if (execute) {
                flushBuffer();
            } else {
                messageBuffer.reset();
            }
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
        }
    };


    // --- HELPERS ---
    @NonNull
    private static BluetoothGattService getBluetoothGattService() {
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
        return service;
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
