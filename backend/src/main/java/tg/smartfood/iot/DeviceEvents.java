package tg.smartfood.iot;

/**
 * Evenements remontes par un coffre (simule ou reel). Le module metier ne recoit que ces evenements :
 * il ignore s'ils viennent du simulateur, d'une requete HTTP d'un ESP32 ou d'un message MQTT.
 */
public final class DeviceEvents {

    private DeviceEvents() {
    }

    /** Le capteur magnetique signale l'ouverture d'une porte. */
    public record DoorOpened(long boxId, int number) {
    }

    /** Le capteur signale la fermeture d'une porte ; latched indique si la serrure s'est enclenchee. */
    public record DoorClosed(long boxId, int number, boolean latched) {
    }

    /** Un code a ete valide sur le clavier central. */
    public record KeypadEntered(long boxId, String code) {
    }

    /** Le coffre s'est connecte ou deconnecte du serveur. */
    public record Connectivity(long boxId, boolean online) {
    }

    /** Mesures periodiques : batterie et temperature interne. */
    public record Heartbeat(long boxId, int batteryPct, double temperatureC) {
    }

    /** Anomalie detectee par le controleur, par exemple DOOR_AJAR (porte mal fermee). */
    public record Anomaly(long boxId, Integer number, String type) {
    }
}
