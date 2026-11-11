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
@Table(name = "iot_devices")
@Getter
@Setter
public class IotDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long boxId;

    private String deviceUid;

    private String secretHash;

    @Enumerated(EnumType.STRING)
    private DeviceKind kind;

    private String firmware = "sim-1.0";

    private boolean online = true;

    private int batteryPct = 100;

    private double temperatureC = 25;

    private String displayText = "";

    private boolean dropNextCommand;

    private Instant lastSeenAt;
}
