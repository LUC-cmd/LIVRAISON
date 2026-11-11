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
@Table(name = "withdrawal_codes")
@Getter
@Setter
public class WithdrawalCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    private Long customerId;

    private Long boxId;

    private Long compartmentId;

    private String codeHash;

    private String codeEncrypted;

    @Enumerated(EnumType.STRING)
    private CodeStatus status = CodeStatus.ACTIVE;

    private Instant expiresAt;

    private Instant usedAt;

    private Instant createdAt = Instant.now();
}
