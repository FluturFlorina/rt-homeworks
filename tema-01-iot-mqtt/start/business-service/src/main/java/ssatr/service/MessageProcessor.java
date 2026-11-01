package ssatr.service;

import java.util.concurrent.BlockingQueue;

/**
 * Preia mesajele de un anumit tip ("telemetry", "alarm" sau "status") din coada
 * inbox, le salvează și actualizează dashboard-ul.
 *
 * Formatul mesajelor (câmpuri separate prin ";"):
 *   telemetry:  deviceId;timestamp;temperatura
 *   alarm:      deviceId;timestamp;temperatura;prag
 *   status:     deviceId;timestamp;text
 */
public class MessageProcessor implements Runnable {

    private final String kind;
    private final BlockingQueue<String> inbox;
    private final CsvStorage storage;
    private final Dashboard dashboard;

    public MessageProcessor(String kind, BlockingQueue<String> inbox, CsvStorage storage, Dashboard dashboard) {
        this.kind = kind;
        this.inbox = inbox;
        this.storage = storage;
        this.dashboard = dashboard;
    }

    @Override
    public void run() {
        // TODO 4: într-o buclă:
        //   - preluați un mesaj: inbox.take()
        //   - salvați mesajul în fișierul CSV: storage.append(kind, mesaj)
        //     (CsvStorage este gata implementată)
        //   - actualizați dashboard-ul, în funcție de kind
        //   - alarmele și confirmările se afișează imediat în consolă
        //   - un mesaj greșit (format invalid) nu trebuie să oprească thread-ul
        try {
            while (!Thread.currentThread().isInterrupted()) {
                //preluare mesaj din coad
                String message = inbox.take();

                // salvarea mesajului in .csv)
                try {
                    storage.append(kind, message);
                } catch (Exception e) {
                    System.err.printf("[storage-error] Eroare la salvarea in CSV (%s): %s%n", kind, e.getMessage());
                }

                // parsare mesajului și actualizare stare
                try {
                    String[] parts = message.split(";");
                    if (parts.length < 3) {
                        System.err.printf("[processor] Mesaj invalid pentru tipul '%s': %s%n", kind, message);
                        continue;
                    }

                    String deviceId = parts[0];
                    long timestamp = Long.parseLong(parts[1]);

                    switch (kind) {
                        case "telemetry":
                            double temp = Double.parseDouble(parts[2]);
                            dashboard.recordTelemetry(deviceId, timestamp, temp);
                            break;

                        case "alarm":
                            double alarmTemp = Double.parseDouble(parts[2]);
                            double threshold = parts.length > 3 ? Double.parseDouble(parts[3]) : 0.0;
                            dashboard.recordAlarm(deviceId, timestamp);
                            // se afiseaza alarmele
                            System.out.printf("%n[Alerta] Dispozitiv: %s | Temp: %.2f °C | Prag: %.2f °C%n",
                                    deviceId, alarmTemp, threshold);
                            break;

                        case "status":
                            String statusText = parts[2];
                            dashboard.recordStatus(deviceId, timestamp, statusText);

                            System.out.printf("%n[STATUS RETEA] Dispozitiv: %s | Raspuns: %s%n",
                                    deviceId, statusText);
                            break;

                        default:
                            System.err.println("[processor] Tip necunoscut de mesaj: " + kind);
                            break;
                    }

                } catch (NumberFormatException nfe) {
                    System.err.printf("[processor] Valoare numerica incorecta în mesajul '%s': %s%n", kind, message);
                } catch (Exception ex) {
                    System.err.printf("[processor] Eroare procesare mesaj '%s': %s%n", kind, ex.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
