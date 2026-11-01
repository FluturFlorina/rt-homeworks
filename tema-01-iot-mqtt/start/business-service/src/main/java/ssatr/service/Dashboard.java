package ssatr.service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ultima stare cunoscută a fiecărui device: ultima temperatură, momentul
 * ultimului mesaj, numărul de alarme etc.
 *
 * Este actualizată de thread-urile MessageProcessor și citită de CommandConsole,
 * deci accesul trebuie sincronizat.
 */
public class Dashboard {

    // TODO 5: alegeți structura de date pentru starea device-urilor
    //   (de exemplu un Map<String, ...> cu cheia deviceId)
    //   și adăugați metodele de actualizare apelate din MessageProcessor.

    // Structura  de date pentru stare dispozitiv
    private final Map<String, DeviceState> devices = new ConcurrentHashMap<>();

    /** Clasa interna care retine starea unui dispozitiv */
    public static class DeviceState {
        private final String deviceId;
        private volatile double lastTemperature = Double.NaN;
        private volatile long lastMessageTimestamp = 0;
        private volatile int alarmCount = 0;
        private volatile String lastStatus = "-";

        public DeviceState(String deviceId) {
            this.deviceId = deviceId;
        }

        public synchronized void updateTelemetry(long timestamp, double temp) {
            this.lastTemperature = temp;
            this.lastMessageTimestamp = Math.max(this.lastMessageTimestamp, timestamp);
        }

        public synchronized void updateAlarm(long timestamp) {
            this.alarmCount++;
            this.lastMessageTimestamp = Math.max(this.lastMessageTimestamp, timestamp);
        }

        public synchronized void updateStatus(long timestamp, String status) {
            this.lastStatus = status;
            this.lastMessageTimestamp = Math.max(this.lastMessageTimestamp, timestamp);
        }
    }

    private DeviceState getOrCreate(String deviceId) {
        return devices.computeIfAbsent(deviceId, DeviceState::new);
    }

    /** Actualizare apelata la primirea unui mesaj de telemetrie. */
    public void recordTelemetry(String deviceId, long timestamp, double temp) {
        getOrCreate(deviceId).updateTelemetry(timestamp, temp);
    }

    /** Actualizare apelata la primirea unei alarme. */
    public void recordAlarm(String deviceId, long timestamp) {
        getOrCreate(deviceId).updateAlarm(timestamp);
    }

    /** Actualizare apelata la primirea unei confirmări de status. */
    public void recordStatus(String deviceId, long timestamp, String status) {
        getOrCreate(deviceId).updateStatus(timestamp, status);
    }


    /** Afiseaza un tabel cu starea tuturor device-urilor. */
    public void print() {
        // TODO 5: afișați câte un rând pentru fiecare device, de exemplu:
        //   DEVICE     TEMP    ULTIMUL MESAJ   ALARME
        //   device-1   23.41   acum 1 s        2
       // System.out.println("(dashboard neimplementat)");

        if (devices.isEmpty()) {
            System.out.println("Nu exista dispozitive inregistrate inca.");
            return;
        }

        long now = System.currentTimeMillis();

        System.out.println("----------------------------------------------------------------------");
        System.out.printf("%-15s %-12s %-20s %-10s%n", "DEVICE", "TEMP (°C)", "ULTIMUL MESAJ", "ALARME");
        System.out.println("----------------------------------------------------------------------");

        for (DeviceState state : devices.values()) {
            String tempStr = Double.isNaN(state.lastTemperature)
                    ? "-"
                    : String.format(Locale.US, "%.2f", state.lastTemperature);

            String ageStr;
            if (state.lastMessageTimestamp <= 0) {
                ageStr = "-";
            } else {
                long ageSec = (now - state.lastMessageTimestamp) / 1000;
                ageStr = ageSec <= 0 ? "chiar acum" : "acum " + ageSec + " s";
            }

            System.out.printf(Locale.US, "%-15s %-12s %-20s %-10d%n",
                    state.deviceId, tempStr, ageStr, state.alarmCount);
        }
        System.out.println("----------------------------------------------------------------------");

    }
}
