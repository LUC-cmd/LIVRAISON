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

public interface WithdrawalCodeRepository extends JpaRepository<WithdrawalCode, Long> {

    Optional<WithdrawalCode> findByBoxIdAndCodeHashAndStatus(Long boxId, String codeHash, CodeStatus status);

    Optional<WithdrawalCode> findByOrderIdAndStatus(Long orderId, CodeStatus status);

    boolean existsByBoxIdAndCodeHashAndStatus(Long boxId, String codeHash, CodeStatus status);

    List<WithdrawalCode> findByStatusAndExpiresAtBefore(CodeStatus status, Instant before);

    Optional<WithdrawalCode> findFirstByOrderIdOrderByIdDesc(Long orderId);

    Optional<WithdrawalCode> findFirstByCompartmentIdAndStatusInOrderByIdDesc(Long compartmentId, Collection<CodeStatus> statuses);
}
