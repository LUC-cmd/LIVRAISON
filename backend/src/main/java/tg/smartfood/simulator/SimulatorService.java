package tg.smartfood.simulator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.box.BoxService;
import tg.smartfood.box.Compartment;
import tg.smartfood.box.CompartmentRepository;
import tg.smartfood.box.CompartmentState;
import tg.smartfood.common.ApiException;
import tg.smartfood.iot.DeviceEvents;
import tg.smartfood.iot.DeviceKind;
import tg.smartfood.iot.HwFault;
import tg.smartfood.iot.IotDevice;
import tg.smartfood.iot.IotDeviceRepository;
import tg.smartfood.iot.SimCompartmentHw;
import tg.smartfood.iot.SimCompartmentHwRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Actions physiques sur le coffre virtuel : pousser une porte, poser ou retirer un repas, taper un code,
 * et injection de pannes. Ces actions ne contournent jamais le serveur : elles ne font que produire
 * les evenements qu'un vrai coffre enverrait.
 */
@Service
@ConditionalOnProperty(name = "smartfood.simulator.enabled", havingValue = "true")
public class SimulatorService {

    public record HwCompartment(int number, boolean doorOpen, boolean latchEngaged, boolean mealPresent, HwFault fault) {
    }

    public record HardwareView(Long boxId, boolean online, int batteryPct, double temperatureC, String displayText,
                               boolean dropNextCommand, List<HwCompartment> compartments) {
    }

    public record DeviceSettings(Boolean online, Integer batteryPct, Double temperatureC, Boolean dropNextCommand) {
    }

    private final SimulatedDeviceGateway gateway;
    private final IotDeviceRepository devices;
    private final CompartmentRepository compartments;
    private final SimCompartmentHwRepository hardware;
    private final ApplicationEventPublisher events;
    private final BoxService boxService;

    public SimulatorService(SimulatedDeviceGateway gateway, IotDeviceRepository devices, CompartmentRepository compartments,
                            SimCompartmentHwRepository hardware, ApplicationEventPublisher events, BoxService boxService) {
        this.gateway = gateway;
        this.devices = devices;
        this.compartments = compartments;
        this.hardware = hardware;
        this.events = events;
        this.boxService = boxService;
    }

    private IotDevice device(long boxId) {
        IotDevice device = devices.findByBoxId(boxId).orElseThrow(() -> ApiException.notFound("Coffre introuvable."));
        if (device.getKind() != DeviceKind.SIMULATED) {
            throw ApiException.conflict("NOT_SIMULATED", "Ce coffre est un appareil reel : il ne se pilote pas depuis le simulateur.");
        }
        return device;
    }

    @Transactional
    public HardwareView view(long boxId) {
        IotDevice device = device(boxId);
        List<HwCompartment> list = new ArrayList<>();
        for (Compartment c : compartments.findByBoxIdOrderByNumberAsc(boxId)) {
            SimCompartmentHw hw = gateway.hw(boxId, c.getNumber());
            list.add(new HwCompartment(c.getNumber(), hw.isDoorOpen(), hw.isLatchEngaged(), hw.isMealPresent(), hw.getFault()));
        }
        return new HardwareView(boxId, device.isOnline(), device.getBatteryPct(), device.getTemperatureC(), device.getDisplayText(),
                device.isDropNextCommand(), list);
    }

    /** Quelqu'un pousse la porte d'un compartiment ouvert. */
    @Transactional
    public HardwareView closeDoor(long boxId, int number) {
        IotDevice device = device(boxId);
        SimCompartmentHw hw = gateway.hw(boxId, number);
        if (!hw.isDoorOpen()) {
            throw ApiException.conflict("DOOR_ALREADY_CLOSED", "La porte est deja fermee.");
        }
        if (hw.getFault() == HwFault.DOOR_AJAR) {
            // La porte ne s'enclenche pas : elle reste entrouverte et le controleur le signale.
            if (device.isOnline()) {
                events.publishEvent(new DeviceEvents.Anomaly(boxId, number, "DOOR_AJAR"));
            }
            return view(boxId);
        }
        hw.setDoorOpen(false);
        hw.setLatchEngaged(hw.getFault() != HwFault.LOCK_BROKEN);
        hardware.save(hw);
        if (device.isOnline() && hw.getFault() != HwFault.SENSOR_BROKEN) {
            events.publishEvent(new DeviceEvents.DoorClosed(boxId, number, hw.isLatchEngaged()));
        }
        return view(boxId);
    }

    /** Quelqu'un pose ou retire un repas : possible uniquement porte ouverte. */
    @Transactional
    public HardwareView setMeal(long boxId, int number, boolean present) {
        device(boxId);
        SimCompartmentHw hw = gateway.hw(boxId, number);
        if (!hw.isDoorOpen()) {
            throw ApiException.conflict("DOOR_CLOSED", "La porte est fermee : impossible d'acceder au compartiment.");
        }
        if (hw.isMealPresent() == present) {
            throw ApiException.conflict("NO_CHANGE", present ? "Un repas est deja dans ce compartiment." : "Ce compartiment est vide.");
        }
        hw.setMealPresent(present);
        hardware.save(hw);
        return view(boxId);
    }

    /** Quelqu'un tape un code sur le clavier central et valide. */
    @Transactional
    public HardwareView keypad(long boxId, String code) {
        IotDevice device = device(boxId);
        if (!device.isOnline()) {
            // Sans connexion, le coffre ne peut pas faire verifier le code : il n'ouvre rien.
            device.setDisplayText("HORS LIGNE - REESSAYEZ");
            devices.save(device);
            return view(boxId);
        }
        events.publishEvent(new DeviceEvents.KeypadEntered(boxId, code));
        return view(boxId);
    }

    // ---------- Injection de pannes (administrateur) ----------

    @Transactional
    public HardwareView updateDevice(long boxId, DeviceSettings settings) {
        IotDevice device = device(boxId);
        if (settings.batteryPct() != null && (settings.batteryPct() < 0 || settings.batteryPct() > 100)) {
            throw ApiException.badRequest("BAD_BATTERY", "La batterie doit etre comprise entre 0 et 100 %.");
        }
        if (settings.temperatureC() != null && (settings.temperatureC() < -20 || settings.temperatureC() > 120)) {
            throw ApiException.badRequest("BAD_TEMPERATURE", "La temperature doit etre comprise entre -20 et 120 °C.");
        }
        if (settings.dropNextCommand() != null) {
            device.setDropNextCommand(settings.dropNextCommand());
            devices.save(device);
        }
        boolean online = device.isOnline();
        if (settings.online() != null && settings.online() != online) {
            online = settings.online();
            events.publishEvent(new DeviceEvents.Connectivity(boxId, online));
            if (online) {
                resync(boxId);
            }
        }
        if (settings.batteryPct() != null || settings.temperatureC() != null) {
            int battery = settings.batteryPct() != null ? settings.batteryPct() : device.getBatteryPct();
            double temperature = settings.temperatureC() != null ? settings.temperatureC() : device.getTemperatureC();
            if (online) {
                events.publishEvent(new DeviceEvents.Heartbeat(boxId, battery, temperature));
            } else {
                device.setBatteryPct(battery);
                device.setTemperatureC(temperature);
                devices.save(device);
            }
        }
        return view(boxId);
    }

    /** A la reconnexion, le coffre renvoie l'etat des portes fermees pendant la coupure. */
    private void resync(long boxId) {
        for (Compartment c : compartments.findByBoxIdOrderByNumberAsc(boxId)) {
            if (c.getState() != CompartmentState.LOADING && c.getState() != CompartmentState.OPEN) {
                continue;
            }
            SimCompartmentHw hw = gateway.hw(boxId, c.getNumber());
            if (!hw.isDoorOpen() && hw.getFault() != HwFault.SENSOR_BROKEN) {
                events.publishEvent(new DeviceEvents.DoorClosed(boxId, c.getNumber(), hw.isLatchEngaged()));
            }
        }
    }

    @Transactional
    public HardwareView setFault(long boxId, int number, HwFault fault) {
        device(boxId);
        SimCompartmentHw hw = gateway.hw(boxId, number);
        hw.setFault(fault);
        hardware.save(hw);
        return view(boxId);
    }

    /** Ouverture forcee d'une porte, sans commande du serveur (effraction ou defaillance mecanique). */
    @Transactional
    public HardwareView forceOpen(long boxId, int number) {
        IotDevice device = device(boxId);
        SimCompartmentHw hw = gateway.hw(boxId, number);
        if (hw.isDoorOpen()) {
            throw ApiException.conflict("DOOR_ALREADY_OPEN", "La porte est deja ouverte.");
        }
        hw.setLatchEngaged(false);
        hw.setDoorOpen(true);
        hardware.save(hw);
        if (device.isOnline() && hw.getFault() != HwFault.SENSOR_BROKEN) {
            events.publishEvent(new DeviceEvents.DoorOpened(boxId, number));
        }
        return view(boxId);
    }

    /** Lance immediatement la surveillance des capteurs, sans attendre le delai de grace. */
    public int runWatchdogNow() {
        return boxService.runWatchdog(Duration.ZERO);
    }

    /** Les coffres simules en ligne envoient leurs mesures toutes les minutes, comme le ferait le firmware. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void heartbeats() {
        for (IotDevice device : devices.findByKindAndOnlineTrue(DeviceKind.SIMULATED)) {
            events.publishEvent(new DeviceEvents.Heartbeat(device.getBoxId(), device.getBatteryPct(), device.getTemperatureC()));
        }
    }
}
