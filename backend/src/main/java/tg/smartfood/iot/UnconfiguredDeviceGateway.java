package tg.smartfood.iot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Passerelle utilisee quand le simulateur est desactive et qu'aucun lien materiel n'est encore branche :
 * tous les coffres sont consideres injoignables, donc rien ne s'ouvre.
 */
@Component
@ConditionalOnProperty(name = "smartfood.simulator.enabled", havingValue = "false")
public class UnconfiguredDeviceGateway implements DeviceGateway {

    @Override
    public UnlockResult unlock(long boxId, int compartmentNumber) {
        return UnlockResult.OFFLINE;
    }

    @Override
    public boolean isLatched(long boxId, int compartmentNumber) {
        return false;
    }

    @Override
    public void display(long boxId, String message) {
        // Aucun ecran joignable : le message est volontairement ignore.
    }
}
