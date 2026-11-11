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
@Table(name = "opening_events")
@Getter
@Setter
public class OpeningEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long boxId;

    private Long compartmentId;

    private Long orderId;

    private Long actorId;

    private String type;

    private boolean success;

    private String detail;

    private Instant createdAt = Instant.now();
}
