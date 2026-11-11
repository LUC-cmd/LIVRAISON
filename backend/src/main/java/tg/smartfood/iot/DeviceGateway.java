package tg.smartfood.iot;

/**
 * Commandes envoyees au controleur d'un coffre. Implementations :
 * le simulateur (version 1) puis une passerelle MQTT sur TLS vers l'ESP32 (version 2).
 */
public interface DeviceGateway {

    enum UnlockResult {
        /** La serrure a ete liberee. */
        OK,
        /** Le coffre n'est pas joignable. */
        OFFLINE,
        /** La commande est partie mais le coffre n'en a pas accuse reception. */
        NO_ACK,
        /** Le coffre a recu la commande mais la serrure n'a pas bouge. */
        LOCK_FAULT
    }

    /** Demande la liberation de la serrure d'un compartiment. */
    UnlockResult unlock(long boxId, int compartmentNumber);

    /** Interroge le coffre : la porte est-elle fermee et la serrure enclenchee ? Faux si le coffre ne repond pas. */
    boolean isLatched(long boxId, int compartmentNumber);

    /** Affiche un message sur l'ecran du coffre. */
    void display(long boxId, String message);
}
