package tg.smartfood.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.common.ApiException;
import tg.smartfood.delivery.DeliveryRepository;
import tg.smartfood.realtime.RealtimeService;
import tg.smartfood.restaurant.RestaurantRepository;
import tg.smartfood.user.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Seul point d'entree pour changer le statut d'une sous-commande : controle la transition, ecrit l'historique, previent les interesses. */
@Service
public class OrderStatusService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.AWAITING_PAYMENT, EnumSet.of(OrderStatus.PENDING, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.REJECTED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.ACCEPTED, EnumSet.of(OrderStatus.READY, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.READY, EnumSet.of(OrderStatus.RESERVED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.RESERVED, EnumSet.of(OrderStatus.LOADED, OrderStatus.READY));
        ALLOWED.put(OrderStatus.LOADED, EnumSet.of(OrderStatus.IN_TRANSIT, OrderStatus.COLLECTED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.IN_TRANSIT, EnumSet.of(OrderStatus.ARRIVED, OrderStatus.COLLECTED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.ARRIVED, EnumSet.of(OrderStatus.COLLECTED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.COLLECTED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.REJECTED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final FoodOrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final RestaurantRepository restaurants;
    private final DeliveryRepository deliveries;
    private final RealtimeService realtime;
    private final UserRepository users;

    public OrderStatusService(FoodOrderRepository orders, OrderStatusHistoryRepository history, RestaurantRepository restaurants,
                              DeliveryRepository deliveries, RealtimeService realtime, UserRepository users) {
        this.users = users;
        this.orders = orders;
        this.history = history;
        this.restaurants = restaurants;
        this.deliveries = deliveries;
        this.realtime = realtime;
    }

    @Transactional
    public FoodOrder transition(FoodOrder order, OrderStatus target, Long actorId, String note) {
        if (!ALLOWED.get(order.getStatus()).contains(target)) {
            throw ApiException.conflict("BAD_TRANSITION",
                    "La commande est au statut " + order.getStatus() + " : passage a " + target + " impossible.");
        }
        order.setStatus(target);
        order.setUpdatedAt(Instant.now());
        FoodOrder saved = orders.save(order);
        record(saved, actorId, note);
        return saved;
    }

    /** Ecrit la ligne d'historique du statut courant et previent le client, le restaurant et le livreur. */
    @Transactional
    public void record(FoodOrder order, Long actorId, String note) {
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setOrderId(order.getId());
        entry.setStatus(order.getStatus());
        entry.setActorId(actorId);
        entry.setNote(note);
        history.save(entry);
        broadcast(order);
    }

    public void broadcast(FoodOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("groupId", order.getGroupId());
        payload.put("status", order.getStatus());
        List<Long> recipients = new ArrayList<>();
        recipients.add(order.getCustomerId());
        restaurants.findById(order.getRestaurantId()).ifPresent(r -> recipients.add(r.getOwnerId()));
        if (order.getDeliveryId() != null) {
            deliveries.findById(order.getDeliveryId()).ifPresent(d -> recipients.add(d.getCourierId()));
        }
        recipients.addAll(users.findAdminIds());
        realtime.pushAll(recipients, "order.updated", payload);
    }
}
