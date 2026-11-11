package tg.smartfood.restaurant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tg.smartfood.user.*;
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
@Table(name = "dishes")
@Getter
@Setter
public class Dish {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long restaurantId;

    private Long categoryId;

    private String name;

    private String description = "";

    private int price;

    @Enumerated(EnumType.STRING)
    private SizeClass sizeClass = SizeClass.MEDIUM;

    private boolean available = true;

    private boolean archived;

    private Instant createdAt = Instant.now();
}
