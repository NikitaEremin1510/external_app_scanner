package by.belblank.echeck.external_app_scanner;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

public class BluetoothStateReceiver extends BroadcastReceiver {

    private final Runnable stateOffCallback;

    BluetoothStateReceiver(@NonNull Runnable stateOffCallback) {
        this.stateOffCallback = stateOffCallback;
    }

    @Override
    public void onReceive(Context context, @NonNull Intent intent) {
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    stateOffCallback.run();
                case BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_TURNING_ON, BluetoothAdapter.STATE_TURNING_OFF:
                    break;
            }
        }
    }
}
