package by.belblank.echeck.external_app_scanner;

import java.util.UUID;


public final class Constants {

    private Constants() {
    }

    // -----------------------
    // Method / Event channels
    // -----------------------
    public static final String METHOD_CHANNEL_NAME = "external_app_scanner/methods";
    public static final String STATUS_EVENT_CHANNEL_NAME = "external_app_scanner/status";
    public static final String DATA_EVENT_CHANNEL_NAME = "external_app_scanner/data";

    // -----------------------
    // Bluetooth service info
    // -----------------------
    public static final UUID SERVICE_UUID = UUID.fromString("1E70A65A-3D42-4F12-B49D-637B939C97C4");
    public static final UUID CHARACTERISTIC_UUID = UUID.fromString("1E70A65B-3D42-4F12-B49D-637B939C97C4");


    // -----------------------
    // Типы статусов
    // -----------------------
    public static final class StatusType {
        public static final String INFO = "INFO";
        public static final String ERROR = "ERROR";
    }


    // -----------------------
    // Список кодов
    // -----------------------

    public static final class Codes {
        // Информационные
        public static final String BT_ENABLED = "BT_ENABLED";
        public static final String BT_DISABLED = "BT_DISABLED";
        public static final String SERVICE_STARTED = "SERVICE_STARTED";
        public static final String SERVICE_STOPPED = "SERVICE_STOPPED";
        public static final String DEVICE_CONNECTED = "DEVICE_CONNECTED";
        public static final String DEVICE_DISCONNECTED = "DEVICE_DISCONNECTED";

        // Ошибки
        public static final String GATT_INIT_FAILED = "GATT_INIT_FAILED";
        public static final String ADVERTISE_FAILED = "ADVERTISE_FAILED";
        public static final String PERMISSION_NOT_GRANTED = "PERMISSION_NOT_GRANTED";
        public static final String ADVERTISE_NOT_SUPPORTED = "ADVERTISE_NOT_SUPPORTED";
        public static final String ALREADY_IN_PROGRESS = "ALREADY_IN_PROGRESS";
        public static final String NO_ACTIVITY = "NO_ACTIVITY";

        public static final String UNKNOWN_STATUS = "UNKNOWN_STATUS";
    }
}

