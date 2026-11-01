package ssatr.device;

import java.util.Locale;
import java.util.concurrent.BlockingQueue;

/**
 * TASK APERIODIC: execută comenzile primite de la business service.
 *
 * Comenzile sosesc oricând, pe MQTT, fără un interval minim între ele, și sunt
 * puse în coada commands. Fiecare comandă eliberează un job al taskului.
 * Comenzi care trebuie suportate:
 *
 *   SET_INTERVAL <ms>       schimbă perioada T a taskului periodic
 *   SET_THRESHOLD <valoare> schimbă pragul de alarmă
 *   RESET                   readuce senzorul la valoarea inițială
 *   STATUS                  raportează perioada și pragul curente
 *
 * După executarea fiecărei comenzi, device-ul publică pe Config.statusTopic(deviceId)
 * o confirmare: "deviceId;timestamp;text", de exemplu
 * "device-1;1727600000000;OK SET_INTERVAL 1000".
 */
public class AperiodicCommandTask implements Runnable {

    private final String deviceId;
    private final TemperatureSensor sensor;
    private final MqttConnection mqtt;
    private final BlockingQueue<String> commands;
    private final PeriodicTelemetryTask periodic;

    public AperiodicCommandTask(String deviceId, TemperatureSensor sensor, MqttConnection mqtt,
                                BlockingQueue<String> commands, PeriodicTelemetryTask periodic) {
        this.deviceId = deviceId;
        this.sensor = sensor;
        this.mqtt = mqtt;
        this.commands = commands;
        this.periodic = periodic;
    }

    @Override
    public void run() {
        // TODO 3: implementați taskul aperiodic:
        //   - preluați comenzile din coadă: commands.take()
        //   - separați numele comenzii de parametru: command.split(" ")
        //   - executați comanda (periodic.setPeriodMs(...), sensor.reset() etc.)
        //   - publicați confirmarea pe Config.statusTopic(deviceId)
        //   - o comandă necunoscută sau un parametru invalid nu trebuie să
        //     oprească taskul: publicați "ERROR <motiv>"
        //
        // Vezi exemplul exemple/01-taskuri/.../AperiodicTaskExample.java

        int jobCount = 0;

        try {
            while (!Thread.currentThread().isInterrupted()) {
                // sosire aperiodica
                String rawCommand = commands.take();//prelueezi comenzile din coada
                long arrivalTime = System.currentTimeMillis();
                jobCount++;

                String responseText;
                String trimmed = rawCommand != null ? rawCommand.trim() : "";

                //parsare comanda
                String[] parts = trimmed.split("\\s+"); //separare nume comanda de parametru
                String commandName = parts.length > 0 ? parts[0].toUpperCase() : "";

                try {
                    switch (commandName) {
                        case "SET_INTERVAL":
                            if (parts.length < 2) {
                                responseText = "ERROR Lipseste parametrul <ms> pentru SET_INTERVAL";
                            } else {
                                long intervalMs = Long.parseLong(parts[1]);
                                if (intervalMs <= 0) {
                                    responseText = "ERROR Intervalul trebuie sa fie pozitiv";
                                } else {
                                    periodic.setPeriodMs(intervalMs);
                                    responseText = "OK SET_INTERVAL " + intervalMs;
                                }
                            }
                            break;

                        case "SET_THRESHOLD":
                            if (parts.length < 2) {
                                responseText = "ERROR Lipseste parametrul <valoare> pentru SET_THRESHOLD";
                            } else {
                                double threshold = Double.parseDouble(parts[1]);
                                periodic.setThreshold(threshold);
                                responseText = String.format(Locale.US, "OK SET_THRESHOLD %.2f", threshold);
                            }
                            break;

                        case "RESET":
                            sensor.reset();
                            responseText = "OK RESET";
                            break;

                        case "STATUS":
                            long currentPeriod = periodic.getPeriodMs();
                            double currentThreshold = periodic.getThreshold();
                            responseText = String.format(Locale.US, "OK STATUS period=%d ms, threshold=%.2f",
                                    currentPeriod, currentThreshold);
                            break;

                        case "":
                            responseText = "ERROR Comanda vida";
                            break;

                        default:
                            responseText = "ERROR Comanda necunoscuta: " + commandName;
                            break;
                    }
                } catch (NumberFormatException ex) {
                    responseText = "ERROR Parametru numeric invalid pentru " + commandName;
                }

                // publicare confirmare pe topicul de status
                long now = System.currentTimeMillis();
                String payload = String.format(Locale.US, "%s;%d;%s", deviceId, now, responseText);
                mqtt.publish(Config.statusTopic(deviceId), payload);

                // calculul timpului de răspuns R
                long responseTime = now - arrivalTime;
                System.out.printf(Locale.US,
                        "   [aperiodic] [%s] job #%d %s executată: R = %d ms -> %s%n",
                        deviceId, jobCount, commandName, responseTime, responseText);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            System.err.println("[" + deviceId + "] Eroare neasteptata in taskul aperiodic: " + ex.getMessage());
        }
    }
    }

