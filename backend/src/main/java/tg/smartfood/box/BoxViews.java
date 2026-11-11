package tg.smartfood.box;

import tg.smartfood.iot.DeviceKind;
import tg.smartfood.restaurant.SizeClass;

import java.time.Instant;
import java.util.List;

public final class BoxViews {

    private BoxViews() {
    }

    /** Compartiment tel que le serveur le connait. orderId n'est renseigne que pour les utilisateurs autorises. */
    public record CompartmentView(Long id, int number, SizeClass size, CompartmentState state, boolean doorOpen, boolean locked,
                                  String faultReason, Long orderId, boolean mine, Instant updatedAt) {
    }

    public record DeviceView(String deviceUid, DeviceKind kind, boolean online, int batteryPct, double temperatureC,
                             String displayText, Instant lastSeenAt) {
    }

    public record BoxView(Long id, String serial, String name, BoxStatus status, Long courierId, String courierName,
                          boolean courierAvailable, Instant keypadLockedUntil, DeviceView device, List<CompartmentView> compartments) {
    }

    /** Resultat de la creation d'un coffre : le secret d'appareil n'est montre qu'une fois. */
    public record CreatedBox(BoxView box, String deviceSecret) {
    }
}
