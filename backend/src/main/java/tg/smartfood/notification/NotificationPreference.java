package tg.smartfood.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tg.smartfood.user.*;
import tg.smartfood.restaurant.*;
import tg.smartfood.box.*;
import tg.smartfood.iot.*;
import tg.smartfood.order.*;
import tg.smartfood.payment.*;
import tg.smartfood.delivery.*;
import tg.smartfood.support.*;
import tg.smartfood.audit.*;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
public class NotificationPreference {
    @Id
    private Long userId;

    private boolean inApp = true;

    private boolean email;

    private boolean sms;

    private boolean push;
}
