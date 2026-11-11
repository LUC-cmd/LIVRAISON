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
@Table(name = "opening_authorizations")
@Getter
@Setter
public class OpeningAuthorization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long compartmentId;

    private Long orderId;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private AuthPurpose purpose;

    private Instant expiresAt;

    private Instant usedAt;

    private Instant createdAt = Instant.now();
}
