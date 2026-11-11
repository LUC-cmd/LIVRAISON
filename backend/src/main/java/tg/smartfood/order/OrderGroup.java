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
@Table(name = "order_groups")
@Getter
@Setter
public class OrderGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long customerId;

    private Long addressId;

    private String deliveryAddress;

    private double deliveryLat;

    private double deliveryLng;

    private int subtotal;

    private int deliveryFee;

    private int total;

    private String idempotencyKey;

    private Instant createdAt = Instant.now();
}
