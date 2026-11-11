package tg.smartfood.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.smartfood.user.*;
import tg.smartfood.restaurant.*;
import tg.smartfood.box.*;
import tg.smartfood.iot.*;
import tg.smartfood.payment.*;
import tg.smartfood.delivery.*;
import tg.smartfood.notification.*;
import tg.smartfood.support.*;
import tg.smartfood.audit.*;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FoodOrderRepository extends JpaRepository<FoodOrder, Long> {

    List<FoodOrder> findByGroupIdOrderByIdAsc(Long groupId);

    List<FoodOrder> findByGroupIdInOrderByIdAsc(Collection<Long> groupIds);

    List<FoodOrder> findByRestaurantIdOrderByIdDesc(Long restaurantId);

    List<FoodOrder> findByDeliveryIdOrderByIdAsc(Long deliveryId);

    List<FoodOrder> findByDeliveryIdInOrderByIdAsc(Collection<Long> deliveryIds);

    List<FoodOrder> findByIdIn(Collection<Long> ids);

    List<FoodOrder> findTop200ByOrderByIdDesc();

    long countByStatus(OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from FoodOrder o where o.id = :id") Optional<FoodOrder> lockById(@Param("id") Long id);
}
