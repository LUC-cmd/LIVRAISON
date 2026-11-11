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
@Table(name = "courier_profiles")
@Getter
@Setter
public class CourierProfile {
    @Id
    private Long userId;

    private boolean available;

    private String vehicle = "Moto";
}
