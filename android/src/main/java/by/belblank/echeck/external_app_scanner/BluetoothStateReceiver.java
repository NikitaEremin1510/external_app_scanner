package by.belblank.echeck.external_app_scanner;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

public class BluetoothStateReceiver extends BroadcastReceiver {

    private final Runnable onBluetoothOffCallback;

    BluetoothStateReceiver(@Nullable Runnable onOffCallback) {
        this.onBluetoothOffCallback = onOffCallback;
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    ScannerEvents.StatusBuilder.info(Constants.Codes.BT_DISABLED).send();
                    break;
                case BluetoothAdapter.STATE_ON:
                    ScannerEvents.StatusBuilder.info(Constants.Codes.BT_ENABLED).send();
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                case BluetoothAdapter.STATE_TURNING_ON:
                    if (onBluetoothOffCallback != null) {
                        onBluetoothOffCallback.run();
                    }
                    break;
            }
        }
    }
}
