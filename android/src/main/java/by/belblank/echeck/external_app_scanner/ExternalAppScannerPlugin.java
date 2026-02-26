package by.belblank.echeck.external_app_scanner;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
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
    private BinaryMessenger binaryMessenger;

    private ExternalAppServerManager serverManager;


    private final BluetoothStateReceiver btStateReceiver = new BluetoothStateReceiver(() -> {
        if (serverManager != null) {
            serverManager.stopServer();
        }
    });

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "getAndroidVersion" -> getAndroidVersion(result);
            case "init" -> init(result);
            case "dispose" -> dispose(result);
            case "enableBT" -> enableBT(result);
            case "start" -> start(result);
            case "stop" -> stop(result);
            case "getStatus" -> getStatus(result);
            default -> result.notImplemented();
        }
    }

    private void dispose(Result result) {
        Logger.d("Disposing...");
        if (serverManager != null) {
            serverManager.stopServer();
        }
        unregisterBtReceiver();

        if (dataEventChannel != null) {
            dataEventChannel.setStreamHandler(null);
            dataEventChannel = null;
        }
        if (statusEventChannel != null) {
            statusEventChannel.setStreamHandler(null);
            statusEventChannel = null;
        }

        ScannerEvents.getDataStream().onCancel(null);
        ScannerEvents.getStatusStream().onCancel(null);
        Logger.d("Disposed");
        result.success(null);
    }

    private void init(Result result) {
        Logger.d("Initializing...");
        registerBtReceiver();
        //  STATUS EVENT CHANNEL
        statusEventChannel = new EventChannel(binaryMessenger, Constants.STATUS_EVENT_CHANNEL_NAME);
        statusEventChannel.setStreamHandler(ScannerEvents.getStatusStream());

        //  DATA EVENT CHANNEL
        dataEventChannel = new EventChannel(binaryMessenger, Constants.DATA_EVENT_CHANNEL_NAME);
        dataEventChannel.setStreamHandler(ScannerEvents.getDataStream());
        Logger.d("Initialized...");
        result.success(null);
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        this.context = binding.getApplicationContext();
        this.binaryMessenger = binding.getBinaryMessenger();

        //  METHOD CHANNEL
        methodChannel = new MethodChannel(binding.getBinaryMessenger(), Constants.METHOD_CHANNEL_NAME);
        methodChannel.setMethodCallHandler(this);
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

        serverManager = new ExternalAppServerManager(context);
        serverManager.startServer();
        result.success(null);
    }

    private void getStatus(Result result) {
        if (serverManager != null) {
            result.success(serverManager.getCurrentStatus());
        } else {
            result.success(Utils.buildStatusMap(
                    Constants.Codes.SERVICE_STOPPED,
                    Constants.StatusType.INFO,
                    null
            ));
        }
    }

    private void stop(MethodChannel.Result result) {
        if (serverManager != null) {
            Logger.d("Stop server");
            serverManager.stopServer();
        }
        result.success(true);
    }

    private void performCleanup() {
        Logger.d("Performing cleanup...");
        if (serverManager != null) {
            serverManager.stopServer();
            serverManager = null;
        }
        unregisterBtReceiver();

        // Сбрасываем хендлеры каналов
        if (dataEventChannel != null) {
            dataEventChannel.setStreamHandler(null);
            dataEventChannel = null;
        }
        if (statusEventChannel != null) {
            statusEventChannel.setStreamHandler(null);
            statusEventChannel = null;
        }

        // Закрываем потоки в синглтонах событий
        ScannerEvents.getDataStream().onCancel(null);
        ScannerEvents.getStatusStream().onCancel(null);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        performCleanup();
        if (methodChannel != null) {
            methodChannel.setMethodCallHandler(null);
            methodChannel = null;
        }
        this.context = null;
        this.binaryMessenger = null;
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
