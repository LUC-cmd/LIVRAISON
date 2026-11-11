package tg.smartfood.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tg.smartfood.restaurant.*;
import tg.smartfood.box.*;
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
@Table(name = "addresses")
@Getter
@Setter
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String label;

    private String line;

    private String city;

    private double latitude;

    private double longitude;

    @Column(name = "is_default")
    private boolean defaultAddress;

    private Instant createdAt = Instant.now();
}
