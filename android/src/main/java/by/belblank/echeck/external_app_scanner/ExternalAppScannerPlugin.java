package by.belblank.echeck.external_app_scanner;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * BluetoothScannerPlugin
 */
public class ExternalAppScannerPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {

    private MethodChannel methodChannel;
    private EventChannel statusEventChannel;
    private EventChannel dataEventChannel;
    private ActivityPluginBinding activityBinding;
    private Context context;

    private ActivityResultListener activityResultListener;
    private boolean isBound;
    private ExternalAppScannerService boundedService;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!(service instanceof ExternalAppScannerService.LocalBinder binder)) return;
            boundedService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            boundedService = null;
            isBound = false;
        }
    };

    private final BluetoothStateReceiver btStateReceiver = new BluetoothStateReceiver(() -> {
        if (isBound && boundedService != null) {
            boundedService.stopSelf();
        }
        unbindBleService();
    });

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "getAndroidVersion" -> getAndroidVersion(result);
            case "enableBT" -> enableBT(result);
            case "start" -> start(result);
            case "stop" -> stop(result);
            case "getStatus" -> getStatus(result);
            default -> result.notImplemented();
        }
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        this.context = binding.getApplicationContext();

        registerBtReceiver();

        Intent intent = new Intent(this.context, ExternalAppScannerService.class);
        bindService(intent);
        //  METHOD CHANNEL
        methodChannel = new MethodChannel(binding.getBinaryMessenger(), Constants.METHOD_CHANNEL_NAME);
        methodChannel.setMethodCallHandler(this);

        //  STATUS EVENT CHANNEL
        statusEventChannel = new EventChannel(binding.getBinaryMessenger(), Constants.STATUS_EVENT_CHANNEL_NAME);
        statusEventChannel.setStreamHandler(ScannerEvents.getStatusStream());

        //  DATA EVENT CHANNEL
        dataEventChannel = new EventChannel(binding.getBinaryMessenger(), Constants.DATA_EVENT_CHANNEL_NAME);
        dataEventChannel.setStreamHandler(ScannerEvents.getDataStream());
    }

    private void registerBtReceiver() {
        if (context == null) return;
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(btStateReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(btStateReceiver, filter);
        }
    }

    private void unregisterBtReceiver() {
        if (context == null) return;
        context.unregisterReceiver(btStateReceiver);
    }

    private void bindService(@NonNull Intent intent) {
        if (!isBound && context != null) {
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void unbindBleService() {
        if (isBound && context != null) {
            context.unbindService(serviceConnection);
            isBound = false;
            boundedService = null;
        }
    }

    private void getAndroidVersion(Result result) {
        result.success(Build.VERSION.SDK_INT);
    }

    private void enableBT(@NonNull Result result) {
        Activity activity = activityBinding.getActivity();
        if (!activity.isFinishing() && activityResultListener != null) {
            activityResultListener.requestBluetoothEnable(result);
        } else {
            result.error(Constants.Codes.NO_ACTIVITY, null, null);
        }
    }

    private void start(MethodChannel.Result result) {
        if (!Utils.isBluetoothEnabled(context)) {
            result.error(Constants.Codes.BT_DISABLED, null, null);
            return;
        }

        if (!Utils.isAdvertiseSupported(context)) {
            result.error(Constants.Codes.ADVERTISE_NOT_SUPPORTED, null, null);
            return;
        }

        if (!Utils.hasPermissions(context)) {
            result.error(Constants.Codes.PERMISSION_NOT_GRANTED, null, null);
            return;
        }

        try {
            Intent intent = new Intent(this.context, ExternalAppScannerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            bindService(intent);
            result.success(true);
        } catch (Exception e) {
            result.error(Constants.Codes.SERVICE_START_FAILED, e.getMessage(), e);
        }
    }

    private void getStatus(Result result) {
        if (isBound && boundedService != null) {
            result.success(boundedService.getCurrentStatus());
        } else {
            // Если сервис не запущен, возвращаем дефолтный статус
            result.success(Utils.buildStatusMap(null, null, null));
        }
    }

    private void stop(MethodChannel.Result result) {
        if (context != null) {
            unbindBleService();
            context.stopService(new Intent(this.context, ExternalAppScannerService.class));
        }
        result.success(true);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (methodChannel != null) {
            methodChannel.setMethodCallHandler(null);
            methodChannel = null;
        }
        if (statusEventChannel != null) {
            statusEventChannel.setStreamHandler(null);
            statusEventChannel = null;
        }
        if (dataEventChannel != null) {
            dataEventChannel.setStreamHandler(null);
            dataEventChannel = null;
        }
        ScannerEvents.getDataStream().onCancel(null);
        ScannerEvents.getStatusStream().onCancel(null);

        unbindBleService();
        unregisterBtReceiver();
        context = null;
        detachActivity();
    }


    private void detachActivity() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(activityResultListener);
            activityResultListener = null;
            activityBinding = null;
        }
    }


    private void attachActivity(@NonNull ActivityPluginBinding binding) {
        this.activityBinding = binding;
        activityResultListener = new ActivityResultListener(activityBinding.getActivity());
        binding.addActivityResultListener(activityResultListener);
    }


    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        attachActivity(binding);
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        attachActivity(binding);
    }


    @Override
    public void onDetachedFromActivityForConfigChanges() {
        detachActivity();
    }

    @Override
    public void onDetachedFromActivity() {
        detachActivity();
    }
}
