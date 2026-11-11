package tg.smartfood.simulator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.box.Compartment;
import tg.smartfood.box.CompartmentRepository;
import tg.smartfood.iot.DeviceEvents;
import tg.smartfood.iot.DeviceGateway;
import tg.smartfood.iot.HwFault;
import tg.smartfood.iot.IotDevice;
import tg.smartfood.iot.IotDeviceRepository;
import tg.smartfood.iot.SimCompartmentHw;
import tg.smartfood.iot.SimCompartmentHwRepository;

/**
 * Coffre virtuel : joue le role de l'ESP32, des relais, des serrures et des capteurs magnetiques.
 * L'etat physique simule est persiste (table sim_compartment_hw) et les evenements remontent
 * par le meme chemin que ceux d'un coffre reel.
 */
@Component
@ConditionalOnProperty(name = "smartfood.simulator.enabled", havingValue = "true")
public class SimulatedDeviceGateway implements DeviceGateway {

    private final IotDeviceRepository devices;
    private final CompartmentRepository compartments;
    private final SimCompartmentHwRepository hardware;
    private final ApplicationEventPublisher events;

    public SimulatedDeviceGateway(IotDeviceRepository devices, CompartmentRepository compartments,
                                  SimCompartmentHwRepository hardware, ApplicationEventPublisher events) {
        this.devices = devices;
        this.compartments = compartments;
        this.hardware = hardware;
        this.events = events;
    }

    /** Etat physique simule d'un compartiment (cree a la demande). */
    @Transactional
    public SimCompartmentHw hw(long boxId, int number) {
        Compartment compartment = compartments.findByBoxIdAndNumber(boxId, number)
                .orElseThrow(() -> new IllegalArgumentException("Compartiment inconnu : coffre " + boxId + ", numero " + number));
        return hardware.findById(compartment.getId()).orElseGet(() -> {
            SimCompartmentHw created = new SimCompartmentHw();
            created.setCompartmentId(compartment.getId());
            return hardware.save(created);
        });
    }

    @Override
    @Transactional
    public UnlockResult unlock(long boxId, int compartmentNumber) {
        IotDevice device = devices.findByBoxId(boxId).orElse(null);
        if (device == null || !device.isOnline()) {
            return UnlockResult.OFFLINE;
        }
        if (device.isDropNextCommand()) {
            // Scenario "commande d'ouverture non recue" : la commande se perd une fois.
            device.setDropNextCommand(false);
            devices.save(device);
            return UnlockResult.NO_ACK;
        }
        SimCompartmentHw hw = hw(boxId, compartmentNumber);
        if (hw.getFault() == HwFault.LOCK_STUCK) {
            return UnlockResult.LOCK_FAULT;
        }
        // La serrure se libere et la porte s'entrouvre sous l'effet du ressort.
        hw.setLatchEngaged(false);
        hw.setDoorOpen(true);
        hardware.save(hw);
        if (hw.getFault() != HwFault.SENSOR_BROKEN) {
            events.publishEvent(new DeviceEvents.DoorOpened(boxId, compartmentNumber));
        }
        return UnlockResult.OK;
    }

    @Override
    @Transactional
    public boolean isLatched(long boxId, int compartmentNumber) {
        IotDevice device = devices.findByBoxId(boxId).orElse(null);
        if (device == null || !device.isOnline()) {
            return false;
        }
        SimCompartmentHw hw = hw(boxId, compartmentNumber);
        return !hw.isDoorOpen() && hw.isLatchEngaged();
    }

    @Override
    @Transactional
    public void display(long boxId, String message) {
        devices.findByBoxId(boxId).ifPresent(device -> {
            device.setDisplayText(message.length() > 80 ? message.substring(0, 80) : message);
            devices.save(device);
        });
    }
}
