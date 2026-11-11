package tg.smartfood.delivery;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.smartfood.user.*;
import tg.smartfood.restaurant.*;
import tg.smartfood.box.*;
import tg.smartfood.iot.*;
import tg.smartfood.order.*;
import tg.smartfood.payment.*;
import tg.smartfood.notification.*;
import tg.smartfood.support.*;
import tg.smartfood.audit.*;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    List<Delivery> findByCourierIdOrderByIdDesc(Long courierId);

    List<Delivery> findByGroupIdOrderByIdAsc(Long groupId);

    Optional<Delivery> findFirstByGroupIdAndStatusInOrderByIdAsc(Long groupId, Collection<DeliveryStatus> statuses);

    boolean existsByCourierIdAndStatusIn(Long courierId, Collection<DeliveryStatus> statuses);

    List<Delivery> findTop200ByOrderByIdDesc();

    Optional<Delivery> findByIdAndCourierId(Long id, Long courierId);

    long countByStatusIn(Collection<DeliveryStatus> statuses);
}
