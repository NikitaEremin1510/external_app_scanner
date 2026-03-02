package by.belblank.echeck.external_app_scanner;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

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
 * ExternalAppScannerPlugin
 */
public class ExternalAppScannerPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    private MethodChannel methodChannel;

    private EventChannel statusEventChannel;
    private EventChannel dataEventChannel;
    private StreamHandlerImpl statusStreamHandler;
    private StreamHandlerImpl dataStreamHandler;

    private ActivityPluginBinding activityBinding;
    private Context context;
    private ActivityResultListener activityResultListener;
    private GattService serverManager;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        this.context = binding.getApplicationContext();

        // METHOD CHANNEL
        methodChannel = new MethodChannel(binding.getBinaryMessenger(), Constants.METHOD_CHANNEL_NAME);
        methodChannel.setMethodCallHandler(this);

        // EVENT CHANNELS
        statusStreamHandler = new StreamHandlerImpl();
        statusEventChannel = new EventChannel(binding.getBinaryMessenger(), Constants.STATUS_EVENT_CHANNEL_NAME);
        statusEventChannel.setStreamHandler(statusStreamHandler);

        dataStreamHandler = new StreamHandlerImpl();
        dataEventChannel = new EventChannel(binding.getBinaryMessenger(), Constants.DATA_EVENT_CHANNEL_NAME);
        dataEventChannel.setStreamHandler(dataStreamHandler);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (serverManager != null) {
            serverManager.stopServer();
            serverManager = null;
        }
        if (methodChannel != null) {
            methodChannel.setMethodCallHandler(null);
            methodChannel = null;
        }
        if (dataEventChannel != null) {
            dataEventChannel.setStreamHandler(null);
            dataEventChannel = null;
        }
        if (statusEventChannel != null) {
            statusEventChannel.setStreamHandler(null);
            statusEventChannel = null;
        }
        this.statusStreamHandler = null;
        this.dataStreamHandler = null;
        this.context = null;
    }

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

    private void getAndroidVersion(@NonNull Result result) {
        result.success(Build.VERSION.SDK_INT);
    }

    private void enableBT(@NonNull Result result) {
        Activity activity = activityBinding.getActivity();
        if (!activity.isFinishing() && activityResultListener != null) {
            activityResultListener.requestBluetoothEnable(result);
        } else {
            result.error(Constants.Code.NO_ACTIVITY, null, null);
        }
    }

    private void start(MethodChannel.Result result) {
        if (!Utils.hasPermissions(context)) {
            result.error(Constants.Code.PERMISSION_NOT_GRANTED, null, null);
            return;
        }

        if (!Utils.isBluetoothEnabled(context)) {
            result.error(Constants.Code.BT_DISABLED, null, null);
            return;
        }

        if (!Utils.isAdvertiseSupported(context)) {
            result.error(Constants.Code.ADVERTISE_NOT_SUPPORTED, null, null);
            return;
        }

        if (serverManager == null) {
            serverManager = new GattService(context, statusStreamHandler, dataStreamHandler);
        }
        serverManager.startServer();
        result.success(null);
    }

    private void getStatus(Result result) {
        if (serverManager != null) {
            result.success(serverManager.getCurrentStatus());
        } else {
            result.success(Utils.buildInfoStatus(Constants.Code.SERVICE_STOPPED, null, null));
        }
    }

    private void stop(MethodChannel.Result result) {
        if (serverManager != null) {
            serverManager.stopServer();
            serverManager = null;
        }
        result.success(null);
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

    private void detachActivity() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(activityResultListener);
            activityResultListener = null;
            activityBinding = null;
        }
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
