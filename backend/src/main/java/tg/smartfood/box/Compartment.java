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
@Table(name = "compartments")
@Getter
@Setter
public class Compartment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long boxId;

    private int number;

    @Enumerated(EnumType.STRING)
    private SizeClass size;

    private int sizeRank;

    @Enumerated(EnumType.STRING)
    private CompartmentState state = CompartmentState.AVAILABLE;

    private boolean doorOpen;

    private boolean locked = true;

    private String faultReason;

    private Instant updatedAt = Instant.now();
}
