package tg.smartfood.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.box.CodeStatus;
import tg.smartfood.box.Compartment;
import tg.smartfood.box.CompartmentAssignment;
import tg.smartfood.box.CompartmentAssignmentRepository;
import tg.smartfood.box.CompartmentRepository;
import tg.smartfood.box.DeliveryBox;
import tg.smartfood.box.DeliveryBoxRepository;
import tg.smartfood.box.WithdrawalCode;
import tg.smartfood.box.WithdrawalCodeRepository;
import tg.smartfood.common.ApiException;
import tg.smartfood.order.OrderViews.*;
import tg.smartfood.payment.Payment;
import tg.smartfood.payment.PaymentRepository;
import tg.smartfood.restaurant.Restaurant;
import tg.smartfood.restaurant.RestaurantRepository;
import tg.smartfood.user.User;
import tg.smartfood.user.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Construit les vues de commandes pour le client, le restaurant et l'administrateur. */
@Service
public class OrderQueryService {

    private final OrderGroupRepository groups;
    private final FoodOrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;
    private final PaymentRepository payments;
    private final RestaurantRepository restaurants;
    private final UserRepository users;
    private final CompartmentAssignmentRepository assignments;
    private final CompartmentRepository compartments;
    private final DeliveryBoxRepository boxes;
    private final WithdrawalCodeRepository codes;

    public OrderQueryService(OrderGroupRepository groups, FoodOrderRepository orders, OrderItemRepository items,
                             OrderStatusHistoryRepository history, PaymentRepository payments, RestaurantRepository restaurants,
                             UserRepository users, CompartmentAssignmentRepository assignments, CompartmentRepository compartments,
                             DeliveryBoxRepository boxes, WithdrawalCodeRepository codes) {
        this.groups = groups;
        this.orders = orders;
        this.items = items;
        this.history = history;
        this.payments = payments;
        this.restaurants = restaurants;
        this.users = users;
        this.assignments = assignments;
        this.compartments = compartments;
        this.boxes = boxes;
        this.codes = codes;
    }

    @Transactional(readOnly = true)
    public List<OrderView> orderViews(List<FoodOrder> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(FoodOrder::getId).toList();
        Map<Long, List<ItemView>> itemsByOrder = new HashMap<>();
        for (OrderItem item : items.findByOrderIdIn(ids)) {
            itemsByOrder.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>())
                    .add(new ItemView(item.getDishId(), item.getDishName(), item.getUnitPrice(), item.getQuantity()));
        }
        Map<Long, List<HistoryView>> historyByOrder = new HashMap<>();
        for (OrderStatusHistory h : history.findByOrderIdInOrderByIdAsc(ids)) {
            historyByOrder.computeIfAbsent(h.getOrderId(), k -> new ArrayList<>())
                    .add(new HistoryView(h.getStatus(), h.getNote(), h.getCreatedAt()));
        }
        Map<Long, Compartment> compartmentByOrder = new HashMap<>();
        for (CompartmentAssignment a : assignments.findByOrderIdInAndActiveTrue(ids)) {
            compartments.findById(a.getCompartmentId()).ifPresent(c -> compartmentByOrder.put(a.getOrderId(), c));
        }
        Map<Long, String> restaurantNames = new HashMap<>();
        Map<Long, String> boxNames = new HashMap<>();
        Instant now = Instant.now();
        List<OrderView> result = new ArrayList<>();
        for (FoodOrder o : list) {
            String restaurantName = restaurantNames.computeIfAbsent(o.getRestaurantId(),
                    id -> restaurants.findById(id).map(Restaurant::getName).orElse(""));
            Compartment c = compartmentByOrder.get(o.getId());
            String boxName = c == null ? null : boxNames.computeIfAbsent(c.getBoxId(),
                    id -> boxes.findById(id).map(DeliveryBox::getName).orElse(""));
            WithdrawalCode code = codes.findFirstByOrderIdOrderByIdDesc(o.getId()).orElse(null);
            boolean codeActive = code != null && code.getStatus() == CodeStatus.ACTIVE && code.getExpiresAt().isAfter(now);
            result.add(new OrderView(o.getId(), o.getGroupId(), o.getRestaurantId(), restaurantName, o.getStatus(), o.getSubtotal(),
                    o.getRequiredSize(), o.getRejectReason(), o.getCreatedAt(), o.getUpdatedAt(),
                    itemsByOrder.getOrDefault(o.getId(), List.of()), historyByOrder.getOrDefault(o.getId(), List.of()),
                    c == null ? null : c.getBoxId(), boxName, c == null ? null : c.getNumber(), c == null ? null : c.getState(),
                    o.getDeliveryId(), codeActive, code == null ? null : code.getExpiresAt()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<GroupView> groupViews(List<OrderGroup> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(OrderGroup::getId).toList();
        Map<Long, List<OrderView>> ordersByGroup = new HashMap<>();
        for (OrderView view : orderViews(orders.findByGroupIdInOrderByIdAsc(ids))) {
            ordersByGroup.computeIfAbsent(view.groupId(), k -> new ArrayList<>()).add(view);
        }
        Map<Long, Payment> paymentByGroup = new HashMap<>();
        payments.findByGroupIdIn(ids).forEach(p -> paymentByGroup.put(p.getGroupId(), p));
        Map<Long, String> customerNames = new HashMap<>();
        List<GroupView> result = new ArrayList<>();
        for (OrderGroup g : list) {
            Payment p = paymentByGroup.get(g.getId());
            String customerName = customerNames.computeIfAbsent(g.getCustomerId(),
                    id -> users.findById(id).map(User::getFullName).orElse(""));
            result.add(new GroupView(g.getId(), g.getCustomerId(), customerName, g.getCreatedAt(), g.getDeliveryAddress(),
                    g.getDeliveryLat(), g.getDeliveryLng(), g.getSubtotal(), g.getDeliveryFee(), g.getTotal(),
                    p == null ? null : new PaymentView(p.getMethod(), p.getStatus(), p.getAmount(), p.getProvider()),
                    ordersByGroup.getOrDefault(g.getId(), List.of())));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public GroupView groupForCustomer(Long groupId, Long customerId) {
        OrderGroup group = groups.findById(groupId)
                .filter(g -> g.getCustomerId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Commande introuvable."));
        return groupViews(List.of(group)).get(0);
    }

    @Transactional(readOnly = true)
    public List<GroupView> groupsForCustomer(Long customerId) {
        return groupViews(groups.findByCustomerIdOrderByIdDesc(customerId));
    }

    @Transactional(readOnly = true)
    public List<RestaurantOrderView> ordersForRestaurant(Long restaurantId) {
        List<FoodOrder> list = orders.findByRestaurantIdOrderByIdDesc(restaurantId).stream()
                .filter(o -> o.getStatus() != OrderStatus.AWAITING_PAYMENT)
                .limit(200)
                .toList();
        Map<Long, FoodOrder> byId = new HashMap<>();
        list.forEach(o -> byId.put(o.getId(), o));
        Map<Long, String> customerNames = new HashMap<>();
        return orderViews(list).stream().map(view -> {
            FoodOrder o = byId.get(view.id());
            String customerName = customerNames.computeIfAbsent(o.getCustomerId(),
                    id -> users.findById(id).map(User::getFullName).orElse(""));
            return new RestaurantOrderView(view, customerName, o.getCommission(), o.getSubtotal() - o.getCommission());
        }).toList();
    }
}
