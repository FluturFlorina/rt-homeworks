package ssatr.device;

import java.util.Locale;
import java.util.concurrent.BlockingQueue;

/**
 * TASK SPORADIC: tratează alarmele de temperatură.
 *
 * Evenimentele sosesc în coada alarmEvents (puse de taskul periodic).
 * Intervalul minim între sosiri este MIN_INTERARRIVAL_MS: între două eliberări
 * ale taskului (două alarme publicate) trebuie să treacă cel puțin atât.
 * Intervalul este impus de cod: evenimentele care sosesc mai des sunt
 * respinse, ca să nu fie inundat brokerul.
 */
public class SporadicAlarmTask implements Runnable {

    static final long MIN_INTERARRIVAL_MS = 5000;

    private final String deviceId;
    private final MqttConnection mqtt;
    private final BlockingQueue<Double> alarmEvents;

    private long lastRelease = 0;
    private final PeriodicTelemetryTask telemetryTask;

    public SporadicAlarmTask(String deviceId, MqttConnection mqtt, BlockingQueue<Double> alarmEvents,
                             PeriodicTelemetryTask telemetryTask) {
        this.deviceId = deviceId;
        this.mqtt = mqtt;
        this.alarmEvents = alarmEvents;
        this.telemetryTask = telemetryTask;
    }

    @Override
    public void run() {
        // TODO 2: implementați taskul sporadic:
        //   - așteptați un eveniment: alarmEvents.take()
        //   - dacă de la ultima eliberare au trecut mai puțin de
        //     MIN_INTERARRIVAL_MS, respingeți evenimentul (afișați un mesaj)
        //   - altfel publicați pe Config.alarmTopic(deviceId) mesajul
        //     "deviceId;timestamp;temperatura;prag"
        //     (pragul curent poate fi transmis împreună cu temperatura sau
        //      citit din PeriodicTelemetryTask; alegeți și justificați în raport)
        //
        // Vezi exemplul exemple/01-taskuri/.../SporadicTaskExample.java

        try {
            while (!Thread.currentThread().isInterrupted()) {
                // asteapta urmatorul eveniment din coada
                Double temperature = alarmEvents.take();
                long now = System.currentTimeMillis();

                // verifica intervalul minim intre sosiri
                //respingi evenimentul daca au trecut mai putin de MIN_INTERARRIVAL_MS
                long sinceLast = now - lastRelease;
                if (lastRelease > 0 && sinceLast < MIN_INTERARRIVAL_MS) {
                    System.out.printf(Locale.US,
                            "   [sporadic] [%s] RESPINS : au trecut doar %d ms (interval minim %d ms)%n",
                            deviceId, sinceLast, MIN_INTERARRIVAL_MS);
                    continue;
                }
                lastRelease = now;

                //citim pragul dinamic
                double threshold = telemetryTask.getThreshold();

                // publicare pmesaj "deviceId;timestamp;temperatura;prag"
               // double threshold = 28.0;
                String payload = String.format(Locale.US, "%s;%d;%.2f;%.2f",
                        deviceId, now, temperature, threshold);

                mqtt.publish(Config.alarmTopic(deviceId), payload);
                System.out.printf(Locale.US, "   [sporadic] [%s] ALARMA TRIMISA: %s%n", deviceId, payload);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
