package by.belblank.echeck.external_app_scanner;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

@SuppressWarnings("deprecation")
public class ActivityResultListener implements PluginRegistry.ActivityResultListener {

    public static final int REQUEST_CODE_ENABLE_BT = 2250;

    private MethodChannel.Result pendingResult;
    private final Activity activity;

    public ActivityResultListener(Activity activity) {
        this.activity = activity;
    }

    public void requestBluetoothEnable(@NonNull MethodChannel.Result result) {

        // Защита от дублирующих вызовов
        if (this.pendingResult != null) {
            result.error(Constants.Codes.ALREADY_IN_PROGRESS, null, null);
            return;
        }

        BluetoothAdapter adapter = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.bluetooth.BluetoothManager manager = (android.bluetooth.BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
            if (manager != null) {
                adapter = manager.getAdapter();
            }
        } else {
            adapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (adapter != null && adapter.isEnabled()) {
            result.success(true);
            return;
        }

        this.pendingResult = result;

        // Проверка разрешений (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {

                result.error(Constants.Codes.PERMISSION_NOT_GRANTED, null, null);
                this.pendingResult = null;
                return;
            }
        }

        // Вызов системного диалога для включения Bluetooth
        try {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, REQUEST_CODE_ENABLE_BT);
        } catch (Exception e) {
            result.error(Constants.Codes.INTENT_FAILED, e.getMessage(), e);
            this.pendingResult = null;
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_ENABLE_BT) {
            if (pendingResult != null) {
                pendingResult.success(resultCode == Activity.RESULT_OK);
                pendingResult = null;
            }
            return true;
        }
        return false;
    }
}
