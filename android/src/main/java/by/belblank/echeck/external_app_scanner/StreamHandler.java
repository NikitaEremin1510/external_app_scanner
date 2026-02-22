package by.belblank.echeck.external_app_scanner;

import android.os.Handler;
import android.os.Looper;

import io.flutter.plugin.common.EventChannel;

public class StreamHandler implements EventChannel.StreamHandler {

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private EventChannel.EventSink eventSink;

    @Override
    public void onListen(Object arguments, EventChannel.EventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    public void onCancel(Object arguments) {
        this.eventSink = null;
    }

    /**
     * Отправляет событие во Flutter.
     * Этот метод можно безопасно вызывать из любого потока.
     *
     * @param data Данные для отправки.
     */
    public void send(final Object data) {
        uiHandler.post(() -> {
            if (eventSink != null) {
                eventSink.success(data);
            }
        });
    }
}