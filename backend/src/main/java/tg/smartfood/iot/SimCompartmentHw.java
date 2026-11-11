package tg.smartfood.iot;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tg.smartfood.user.*;
import tg.smartfood.restaurant.*;
import tg.smartfood.box.*;
import tg.smartfood.order.*;
import tg.smartfood.payment.*;
import tg.smartfood.delivery.*;
import tg.smartfood.notification.*;
import tg.smartfood.support.*;
import tg.smartfood.audit.*;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "sim_compartment_hw")
@Getter
@Setter
public class SimCompartmentHw {
    @Id
    private Long compartmentId;

    private boolean doorOpen;

    private boolean latchEngaged = true;

    private boolean mealPresent;

    @Enumerated(EnumType.STRING)
    private HwFault fault = HwFault.NONE;
}
