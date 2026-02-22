package by.belblank.echeck.external_app_scanner;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class ScannerEvents {
    private static final StreamHandler statusStream = new StreamHandler();

    private static final StreamHandler dataStream = new StreamHandler();


    public static StreamHandler getStatusStream() {
        return statusStream;
    }

    public static StreamHandler getDataStream() {
        return dataStream;
    }

    /**
     * Билдер для системных событий (ошибки, изменения состояния, подключения)
     */
    public static class StatusBuilder {
        private final String type;
        private final String code;
        private final Map<String, Object> deviceMap = new HashMap<>();

        public StatusBuilder(@NonNull String type, @NonNull String code) {
            this.type = type;
            this.code = code;
        }

        public static StatusBuilder info(@NonNull String code) {
            return new StatusBuilder(Constants.StatusType.INFO, code);
        }

        public static StatusBuilder error(@NonNull String code) {
            return new StatusBuilder(Constants.StatusType.ERROR, code);
        }

        /**
         * Добавить в сообщение устройство.
         *
         * @param deviceMap параметры устройства
         */
        public StatusBuilder addDevice(@NonNull Map<String, Object> deviceMap) {
            this.deviceMap.putAll(deviceMap);
            return this;
        }

        /**
         * Отправить событие.
         */
        public void send() {
            final Map<String, Object> event = Utils.buildStatusMap(code, type, deviceMap);
            Logger.i("Event sent: " + event);
            statusStream.send(event);
        }
    }


    public static class Data {
        private final String code;

        /**
         * Билдер для сообщений (отсканированные данные)
         *
         * @param data сообщение
         */
        public Data(@NonNull String data) {
            this.code = data;
        }

        public void send() {
            Logger.i("Data sent: " + code);
            dataStream.send(code);
        }
    }
}