package tg.smartfood.iot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tg.smartfood.audit.AuditService;
import tg.smartfood.common.ApiException;

/**
 * Point d'entree HTTPS des evenements d'un coffre reel (ESP32). Chaque appareil s'authentifie avec son identifiant
 * et son secret propres. Les evenements sont convertis en DeviceEvents, exactement comme ceux du simulateur.
 * Les commandes vers le coffre (ouverture, affichage) passeront par la passerelle MQTT sur TLS de la version 2.
 */
@RestController
@RequestMapping("/api/v1/device")
public class DeviceController {

    public record DeviceEventRequest(@NotBlank String type, Integer compartment, Boolean latched, String code,
                                     Integer batteryPct, Double temperatureC, String anomaly) {
    }

    private final IotDeviceRepository devices;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    public DeviceController(IotDeviceRepository devices, PasswordEncoder passwordEncoder, ApplicationEventPublisher events,
                            AuditService audit) {
        this.devices = devices;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.audit = audit;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public void receive(@RequestHeader("X-Device-Uid") String deviceUid, @RequestHeader("X-Device-Secret") String secret,
                        @Valid @RequestBody DeviceEventRequest request) {
        IotDevice device = devices.findByDeviceUid(deviceUid).orElse(null);
        if (device == null || device.getKind() != DeviceKind.ESP32 || !passwordEncoder.matches(secret, device.getSecretHash())) {
            audit.security("DEVICE_AUTH_FAILED", null, device == null ? null : device.getBoxId(), "Appareil " + deviceUid);
            throw ApiException.unauthorized("DEVICE_AUTH_FAILED", "Appareil non reconnu.");
        }
        long boxId = device.getBoxId();
        if (!device.isOnline()) {
            events.publishEvent(new DeviceEvents.Connectivity(boxId, true));
        }
        switch (request.type()) {
            case "DOOR_OPENED" -> events.publishEvent(new DeviceEvents.DoorOpened(boxId, requireCompartment(request)));
            case "DOOR_CLOSED" -> events.publishEvent(new DeviceEvents.DoorClosed(boxId, requireCompartment(request),
                    Boolean.TRUE.equals(request.latched())));
            case "KEYPAD" -> events.publishEvent(new DeviceEvents.KeypadEntered(boxId, request.code()));
            case "HEARTBEAT" -> {
                if (request.batteryPct() == null || request.temperatureC() == null) {
                    throw ApiException.badRequest("BAD_EVENT", "batteryPct et temperatureC sont obligatoires.");
                }
                events.publishEvent(new DeviceEvents.Heartbeat(boxId, Math.max(0, Math.min(100, request.batteryPct())), request.temperatureC()));
            }
            case "ANOMALY" -> events.publishEvent(new DeviceEvents.Anomaly(boxId, request.compartment(),
                    request.anomaly() == null ? "UNKNOWN" : request.anomaly()));
            case "OFFLINE" -> events.publishEvent(new DeviceEvents.Connectivity(boxId, false));
            default -> throw ApiException.badRequest("BAD_EVENT", "Type d'evenement inconnu.");
        }
    }

    private static int requireCompartment(DeviceEventRequest request) {
        if (request.compartment() == null) {
            throw ApiException.badRequest("BAD_EVENT", "Le numero de compartiment est obligatoire.");
        }
        return request.compartment();
    }
}
