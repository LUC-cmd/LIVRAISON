package tg.smartfood.simulator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tg.smartfood.audit.AuditService;
import tg.smartfood.box.BoxService;
import tg.smartfood.common.AuthUser;
import tg.smartfood.iot.HwFault;
import tg.smartfood.simulator.SimulatorService.DeviceSettings;
import tg.smartfood.simulator.SimulatorService.HardwareView;

import java.util.Map;

/**
 * API du coffre virtuel. Les actions physiques sont ouvertes a tout utilisateur qui voit le coffre
 * (comme toute personne presente devant un coffre reel) ; l'injection de pannes est reservee aux administrateurs.
 */
@RestController
@RequestMapping("/api/v1/simulator")
@ConditionalOnProperty(name = "smartfood.simulator.enabled", havingValue = "true")
public class SimulatorController {

    public record KeypadRequest(@NotBlank(message = "Saisissez un code.") @Size(max = 12, message = "Code trop long.") String code) {
    }

    public record MealRequest(boolean present) {
    }

    public record FaultRequest(@NotNull(message = "Choisissez une panne.") HwFault fault) {
    }

    private final SimulatorService simulator;
    private final BoxService boxService;
    private final AuditService audit;

    public SimulatorController(SimulatorService simulator, BoxService boxService, AuditService audit) {
        this.simulator = simulator;
        this.boxService = boxService;
        this.audit = audit;
    }

    @GetMapping("/boxes/{boxId}")
    public HardwareView view(@AuthenticationPrincipal AuthUser user, @PathVariable Long boxId) {
        boxService.visibleBox(boxId, user);
        return simulator.view(boxId);
    }

    @PostMapping("/boxes/{boxId}/compartments/{number}/close")
    public HardwareView close(@AuthenticationPrincipal AuthUser user, @PathVariable Long boxId, @PathVariable int number) {
        boxService.visibleBox(boxId, user);
        return simulator.closeDoor(boxId, number);
    }

    @PostMapping("/boxes/{boxId}/compartments/{number}/meal")
    public HardwareView meal(@AuthenticationPrincipal AuthUser user, @PathVariable Long boxId, @PathVariable int number,
                             @RequestBody MealRequest request) {
        boxService.visibleBox(boxId, user);
        return simulator.setMeal(boxId, number, request.present());
    }

    @PostMapping("/boxes/{boxId}/keypad")
    public HardwareView keypad(@AuthenticationPrincipal AuthUser user, @PathVariable Long boxId, @Valid @RequestBody KeypadRequest request) {
        boxService.visibleBox(boxId, user);
        return simulator.keypad(boxId, request.code());
    }

    @PutMapping("/admin/boxes/{boxId}/device")
    public HardwareView device(@AuthenticationPrincipal AuthUser admin, @PathVariable Long boxId, @RequestBody DeviceSettings settings) {
        audit.log(admin.id(), "SIM_DEVICE_SETTINGS", "BOX", boxId, String.valueOf(settings));
        return simulator.updateDevice(boxId, settings);
    }

    @PutMapping("/admin/boxes/{boxId}/compartments/{number}/fault")
    public HardwareView fault(@AuthenticationPrincipal AuthUser admin, @PathVariable Long boxId, @PathVariable int number,
                              @Valid @RequestBody FaultRequest request) {
        audit.log(admin.id(), "SIM_FAULT", "BOX", boxId, "Compartiment " + number + " : " + request.fault());
        return simulator.setFault(boxId, number, request.fault());
    }

    @PostMapping("/admin/boxes/{boxId}/compartments/{number}/force-open")
    public HardwareView forceOpen(@AuthenticationPrincipal AuthUser admin, @PathVariable Long boxId, @PathVariable int number) {
        audit.log(admin.id(), "SIM_FORCE_OPEN", "BOX", boxId, "Compartiment " + number);
        return simulator.forceOpen(boxId, number);
    }

    @PostMapping("/admin/watchdog")
    public Map<String, Object> watchdog() {
        return Map.of("incidents", simulator.runWatchdogNow());
    }
}
