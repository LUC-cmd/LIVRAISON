package tg.smartfood.box;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tg.smartfood.user.*;
import tg.smartfood.restaurant.*;
import tg.smartfood.iot.*;
import tg.smartfood.order.*;
import tg.smartfood.payment.*;
import tg.smartfood.delivery.*;
import tg.smartfood.notification.*;
import tg.smartfood.support.*;
import tg.smartfood.audit.*;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "delivery_boxes")
@Getter
@Setter
public class DeliveryBox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String serial;

    private String name;

    private Long courierId;

    @Enumerated(EnumType.STRING)
    private BoxStatus status = BoxStatus.ACTIVE;

    private int failedAttempts;

    private Instant keypadLockedUntil;

    private Instant createdAt = Instant.now();
}
