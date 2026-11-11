package tg.smartfood.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tg.smartfood.user.*;
import tg.smartfood.restaurant.*;
import tg.smartfood.box.*;
import tg.smartfood.iot.*;
import tg.smartfood.payment.*;
import tg.smartfood.delivery.*;
import tg.smartfood.notification.*;
import tg.smartfood.support.*;
import tg.smartfood.audit.*;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class FoodOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long groupId;

    private Long restaurantId;

    private Long customerId;

    private Long deliveryId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private int subtotal;

    private int commission;

    @Enumerated(EnumType.STRING)
    private SizeClass requiredSize;

    private String rejectReason;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();
}
