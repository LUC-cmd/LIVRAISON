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

public interface CompartmentRepository extends JpaRepository<Compartment, Long> {

    List<Compartment> findByBoxIdOrderByNumberAsc(Long boxId);

    Optional<Compartment> findByBoxIdAndNumber(Long boxId, int number);

    long countByBoxIdAndState(Long boxId, CompartmentState state);

    List<Compartment> findByStateAndUpdatedAtBefore(CompartmentState state, Instant before);

    List<Compartment> findByBoxIdIn(Collection<Long> boxIds);

    long countByState(CompartmentState state);

    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from Compartment c where c.id = :id") Optional<Compartment> lockById(@Param("id") Long id);

    @Query(value = "SELECT * FROM compartments WHERE box_id = :boxId AND state = 'AVAILABLE' AND size_rank >= :minRank ORDER BY size_rank, number LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true) Optional<Compartment> lockFirstAvailable(@Param("boxId") Long boxId, @Param("minRank") int minRank);
}
