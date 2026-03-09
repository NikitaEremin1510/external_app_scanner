package by.belblank.echeck.external_app_scanner;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Utils {

    private Utils() {
    }

    /**
     * Проверка: включен ли Bluetooth.
     */
    public static boolean isBluetoothEnabled(@NonNull Context context) {
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) return false;

        final BluetoothAdapter adapter = bluetoothManager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    /**
     * Проверка: поддерживает ли устройство режим рекламы (Advertising).
     */
    public static boolean isAdvertiseSupported(@NonNull Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false;
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = bluetoothManager.getAdapter();

        // На некоторых устройствах чип BLE есть, но транслировать пакеты он не умеет
        return adapter != null && adapter.isMultipleAdvertisementSupported();
    }

    /**
     * Проверка наличия всех необходимых разрешений.
     */
    public static boolean hasPermissions(@NonNull Context context) {
        return getMissingPermissions(context).isEmpty();
    }

    /**
     * Внутренний метод для сбора недостающих разрешений.
     */
    @NonNull
    private static List<String> getMissingPermissions(@NonNull Context context) {
        final List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12 (S) and above
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 (Q) and 11 (R)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For Android 6 (M) to 9 (P)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        final List<String> missing = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }
        return missing;
    }


    @NonNull
    public static HashMap<String, Object> buildInfoStatus(@NonNull String code,
                                                          @Nullable Map<String, Object> device,
                                                          @Nullable String advertisingName) {
        final HashMap<String, Object> status = new HashMap<>();

        status.put("code", code);
        status.put("type", Constants.StatusType.INFO);
        if (device != null && !device.isEmpty()) {
            status.put("device", device);
        }
        if(advertisingName != null){
            status.put("advertising_name", advertisingName);
        }
        return status;
    }

    @NonNull
    public static HashMap<String, Object> buildErrorStatus(@Nullable String code) {
        final HashMap<String, Object> status = new HashMap<>();
        status.put("code", code == null ? Constants.Code.UNKNOWN_STATUS : code);
        status.put("type", Constants.StatusType.ERROR);
        return status;
    }
}
