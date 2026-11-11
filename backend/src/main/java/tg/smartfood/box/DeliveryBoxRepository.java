package tg.smartfood.box;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryBoxRepository extends JpaRepository<DeliveryBox, Long> {

    Optional<DeliveryBox> findByCourierId(Long courierId);

    List<DeliveryBox> findByStatusOrderByIdAsc(BoxStatus status);

    List<DeliveryBox> findAllByOrderByIdAsc();

    boolean existsBySerial(String serial);

    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from DeliveryBox b where b.id = :id") Optional<DeliveryBox> lockById(@Param("id") Long id);
}
