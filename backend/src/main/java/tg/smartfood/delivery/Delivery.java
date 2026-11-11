package tg.smartfood.delivery;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tg.smartfood.user.*;
import tg.smartfood.restaurant.*;
import tg.smartfood.box.*;
import tg.smartfood.iot.*;
import tg.smartfood.order.*;
import tg.smartfood.payment.*;
import tg.smartfood.notification.*;
import tg.smartfood.support.*;
import tg.smartfood.audit.*;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "deliveries")
@Getter
@Setter
public class Delivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long groupId;

    private Long boxId;

    private Long courierId;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus status = DeliveryStatus.OFFERED;

    private double destLat;

    private double destLng;

    private int courierEarning;

    private Instant createdAt = Instant.now();

    private Instant pickedUpAt;

    private Instant arrivedAt;

    private Instant completedAt;
}
