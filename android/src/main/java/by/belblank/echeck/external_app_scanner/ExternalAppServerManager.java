package by.belblank.echeck.external_app_scanner;

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

public class ExternalAppServerManager {

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final BluetoothLeAdvertiser advertiser;
    private final ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();

    private BluetoothGattServer gattServer;
    private BluetoothDevice currentDevice;
    private boolean isRunning;

    public ExternalAppServerManager(Context context) {
        this.context = context;
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
        startGattServer();
        if (currentDevice == null) {
            startAdvertising();
        }
        isRunning = true;
    }

    public void stopServer() {
        stopAdvertising();
        stopGattServer();
        Logger.d("Server stopped.");
        isRunning = false;
        ScannerEvents.StatusBuilder.info(Constants.Codes.SERVICE_STOPPED).send();
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
        }
    }

    private void startGattServer() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
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
        }
    }

    private void flushBuffer() {
        if (messageBuffer.size() > 0) {
            String message = new String(messageBuffer.toByteArray(), StandardCharsets.UTF_8);
            new ScannerEvents.Data(message).send();
            messageBuffer.reset();
        }
    }


    // --- CALLBACKS ---
    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Logger.i("Advertising started. Settings: " + settingsInEffect.toString() + ".");
            if (currentDevice == null) {
                ScannerEvents.StatusBuilder.info(Constants.Codes.SERVICE_STARTED).send();
            } else {
                Logger.d("Skip SERVICE_STARTED status because device already connected.");
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            Logger.w("Advertising start failure. Error code: " + errorCode + ".");
            ScannerEvents.StatusBuilder.error(Constants.Codes.ADVERTISE_FAILED).send();
        }
    };

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            // Устройство подключено
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Logger.i("Device connected. Device name: " + device.getName() + " Device address: [" + device.getAddress() + "]");
                currentDevice = device;
                ScannerEvents.StatusBuilder.info(Constants.Codes.DEVICE_CONNECTED)
                        .addDevice(createBluetoothDeviceMap(device))
                        .send();
                if (isRunning) {
                    new Handler(Looper.getMainLooper()).postDelayed(
                            () -> stopAdvertising(), 1000L
                    );
                }
            }
            // Устройство отключено
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Logger.i("Device disconnected. Device name: " + device.getName() + " Device address: [" + device.getAddress() + "]. Status: " + status);
                messageBuffer.reset();
                currentDevice = null;

                ScannerEvents.StatusBuilder.info(Constants.Codes.DEVICE_DISCONNECTED).send();
                startAdvertising();
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

                    // Если сообщение заканчивается спецсимволом (CR/LF)
                    // или мы просто решили, что каждый Write — это отдельное событие:
                    String currentData = new String(messageBuffer.toByteArray(), StandardCharsets.UTF_8);
                    if (currentData.endsWith("\n") || currentData.endsWith("\r") || offset == 0) {
                        new ScannerEvents.Data(currentData.trim()).send();
                        messageBuffer.reset();
                    }
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
