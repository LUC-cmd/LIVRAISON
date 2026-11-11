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

public interface CompartmentAssignmentRepository extends JpaRepository<CompartmentAssignment, Long> {

    Optional<CompartmentAssignment> findByCompartmentIdAndActiveTrue(Long compartmentId);

    Optional<CompartmentAssignment> findByOrderIdAndActiveTrue(Long orderId);

    List<CompartmentAssignment> findByOrderIdInAndActiveTrue(Collection<Long> orderIds);

    List<CompartmentAssignment> findByCompartmentIdInAndActiveTrue(Collection<Long> compartmentIds);
}
