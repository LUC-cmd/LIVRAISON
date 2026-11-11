package tg.smartfood.iot;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tg.smartfood.user.*;
import tg.smartfood.restaurant.*;
import tg.smartfood.box.*;
import tg.smartfood.order.*;
import tg.smartfood.payment.*;
import tg.smartfood.delivery.*;
import tg.smartfood.notification.*;
import tg.smartfood.support.*;
import tg.smartfood.audit.*;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "device_heartbeats")
@Getter
@Setter
public class DeviceHeartbeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long deviceId;

    private int batteryPct;

    private double temperatureC;

    private Instant createdAt = Instant.now();
}
