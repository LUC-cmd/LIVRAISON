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
@Table(name = "delivery_assignments")
@Getter
@Setter
public class DeliveryAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long deliveryId;

    private Long courierId;

    @Enumerated(EnumType.STRING)
    private AssignmentStatus status = AssignmentStatus.OFFERED;

    private Instant createdAt = Instant.now();

    private Instant respondedAt;
}
