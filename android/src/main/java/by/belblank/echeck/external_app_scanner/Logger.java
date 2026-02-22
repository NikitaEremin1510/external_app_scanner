package by.belblank.echeck.external_app_scanner;

import io.flutter.BuildConfig;

public class Logger {
    private static final String TAG = "ExtAppScannerPlugin";

    public static void d(String message) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d(TAG, message);
        }
    }

    public static void e(String message, Throwable throwable) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e(TAG, message, throwable);
        }
    }

    public static void i(String message) {
        if (BuildConfig.DEBUG) {
            android.util.Log.i(TAG, message);
        }
    }

    public static void w(String message) {
        if (BuildConfig.DEBUG) {
            android.util.Log.w(TAG, message);
        }
    }
}