package tg.smartfood.order;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.audit.AuditService;
import tg.smartfood.box.BoxService;
import tg.smartfood.common.ApiException;
import tg.smartfood.common.AppProps;
import tg.smartfood.common.AuthUser;
import tg.smartfood.notification.NotificationService;
import tg.smartfood.order.OrderViews.CheckoutRequest;
import tg.smartfood.order.OrderViews.GroupView;
import tg.smartfood.order.OrderViews.Line;
import tg.smartfood.payment.Payment;
import tg.smartfood.payment.PaymentCompleted;
import tg.smartfood.payment.PaymentMethod;
import tg.smartfood.payment.PaymentService;
import tg.smartfood.payment.PaymentStatus;
import tg.smartfood.restaurant.Dish;
import tg.smartfood.restaurant.DishRepository;
import tg.smartfood.restaurant.Restaurant;
import tg.smartfood.restaurant.RestaurantRepository;
import tg.smartfood.restaurant.RestaurantService;
import tg.smartfood.restaurant.RestaurantStatus;
import tg.smartfood.restaurant.SizeClass;
import tg.smartfood.user.Address;
import tg.smartfood.user.AddressRepository;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrderService {

    private static final Set<OrderStatus> DEAD = EnumSet.of(OrderStatus.REJECTED, OrderStatus.CANCELLED);

    private final OrderGroupRepository groups;
    private final FoodOrderRepository orders;
    private final OrderItemRepository items;
    private final DishRepository dishes;
    private final RestaurantRepository restaurants;
    private final RestaurantService restaurantService;
    private final AddressRepository addresses;
    private final PaymentService payments;
    private final OrderStatusService orderStatus;
    private final OrderQueryService queries;
    private final BoxService boxService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final AppProps props;

    public OrderService(OrderGroupRepository groups, FoodOrderRepository orders, OrderItemRepository items, DishRepository dishes,
                        RestaurantRepository restaurants, RestaurantService restaurantService, AddressRepository addresses,
                        PaymentService payments, OrderStatusService orderStatus, OrderQueryService queries, BoxService boxService,
                        NotificationService notifications, AuditService audit, AppProps props) {
        this.groups = groups;
        this.orders = orders;
        this.items = items;
        this.dishes = dishes;
        this.restaurants = restaurants;
        this.restaurantService = restaurantService;
        this.addresses = addresses;
        this.payments = payments;
        this.orderStatus = orderStatus;
        this.queries = queries;
        this.boxService = boxService;
        this.notifications = notifications;
        this.audit = audit;
        this.props = props;
    }

    // =====================================================================
    // Passage de commande
    // =====================================================================

    /**
     * Enregistre une commande globale. Les plats sont regroupes par restaurant : chaque restaurant recoit
     * sa propre sous-commande. Les prix sont relus en base ; ceux du navigateur ne sont jamais utilises.
     */
    @Transactional
    public GroupView checkout(AuthUser customer, CheckoutRequest request, String idempotencyKey) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null) {
            if (key.length() > 64) {
                throw ApiException.badRequest("BAD_IDEMPOTENCY_KEY", "La cle d'idempotence est trop longue.");
            }
            OrderGroup existing = groups.findByCustomerIdAndIdempotencyKey(customer.id(), key).orElse(null);
            if (existing != null) {
                return queries.groupViews(List.of(existing)).get(0);
            }
        }
        Address address = addresses.findByIdAndUserId(request.addressId(), customer.id())
                .orElseThrow(() -> ApiException.badRequest("BAD_ADDRESS", "Cette adresse n'existe pas dans votre compte."));

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (Line line : request.items()) {
            if (line.dishId() == null || line.quantity() < 1 || line.quantity() > 50) {
                throw ApiException.badRequest("BAD_LINE", "Une ligne du panier est invalide.");
            }
            quantities.merge(line.dishId(), line.quantity(), Integer::sum);
        }
        Map<Long, List<Dish>> dishesByRestaurant = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            if (entry.getValue() > 50) {
                throw ApiException.badRequest("BAD_LINE", "Quantite maximale par plat : 50.");
            }
            Dish dish = dishes.findById(entry.getKey()).filter(d -> !d.isArchived())
                    .orElseThrow(() -> ApiException.badRequest("DISH_GONE", "Un plat de votre panier n'existe plus."));
            if (!dish.isAvailable()) {
                throw ApiException.conflict("DISH_UNAVAILABLE", "Le plat « " + dish.getName() + " » n'est plus disponible.");
            }
            dishesByRestaurant.computeIfAbsent(dish.getRestaurantId(), k -> new ArrayList<>()).add(dish);
        }

        int subtotal = 0;
        Map<Long, Restaurant> restaurantById = new LinkedHashMap<>();
        for (Long restaurantId : dishesByRestaurant.keySet()) {
            Restaurant restaurant = restaurants.findById(restaurantId)
                    .filter(r -> r.getStatus() == RestaurantStatus.APPROVED)
                    .orElseThrow(() -> ApiException.conflict("RESTAURANT_UNAVAILABLE", "Un restaurant de votre panier n'est plus disponible."));
            if (!restaurantService.isOpenNow(restaurant)) {
                throw ApiException.conflict("RESTAURANT_CLOSED", "Le restaurant « " + restaurant.getName() + " » est ferme pour le moment.");
            }
            restaurantById.put(restaurantId, restaurant);
            for (Dish dish : dishesByRestaurant.get(restaurantId)) {
                subtotal += dish.getPrice() * quantities.get(dish.getId());
            }
        }

        OrderGroup group = new OrderGroup();
        group.setCustomerId(customer.id());
        group.setAddressId(address.getId());
        group.setDeliveryAddress(address.getLine() + ", " + address.getCity());
        group.setDeliveryLat(address.getLatitude());
        group.setDeliveryLng(address.getLongitude());
        group.setSubtotal(subtotal);
        group.setDeliveryFee(props.getDeliveryFee());
        group.setTotal(subtotal + props.getDeliveryFee());
        group.setIdempotencyKey(key);
        group = groups.save(group);

        Payment payment = payments.create(group.getId(), request.paymentMethod(), group.getTotal());
        boolean payLater = request.paymentMethod() == PaymentMethod.CASH_ON_DELIVERY;

        for (Map.Entry<Long, List<Dish>> entry : dishesByRestaurant.entrySet()) {
            Restaurant restaurant = restaurantById.get(entry.getKey());
            int orderSubtotal = 0;
            int totalQuantity = 0;
            SizeClass size = SizeClass.SMALL;
            for (Dish dish : entry.getValue()) {
                int quantity = quantities.get(dish.getId());
                orderSubtotal += dish.getPrice() * quantity;
                totalQuantity += quantity;
                if (dish.getSizeClass().rank() > size.rank()) {
                    size = dish.getSizeClass();
                }
            }
            // Au-dela de trois articles, la commande passe au gabarit de compartiment superieur.
            if (totalQuantity > 3) {
                size = size.larger();
            }
            FoodOrder order = new FoodOrder();
            order.setGroupId(group.getId());
            order.setRestaurantId(restaurant.getId());
            order.setCustomerId(customer.id());
            order.setStatus(payLater ? OrderStatus.PENDING : OrderStatus.AWAITING_PAYMENT);
            order.setSubtotal(orderSubtotal);
            order.setCommission(Math.round(orderSubtotal * props.getCommissionPercent() / 100f));
            order.setRequiredSize(size);
            order = orders.save(order);
            for (Dish dish : entry.getValue()) {
                OrderItem item = new OrderItem();
                item.setOrderId(order.getId());
                item.setDishId(dish.getId());
                item.setDishName(dish.getName());
                item.setUnitPrice(dish.getPrice());
                item.setQuantity(quantities.get(dish.getId()));
                items.save(item);
            }
            orderStatus.record(order, customer.id(), "Commande enregistree");
            if (payLater) {
                notifyNewOrder(restaurant, order);
            }
        }
        audit.log(customer.id(), "ORDER_PLACED", "ORDER_GROUP", group.getId(),
                group.getTotal() + " FCFA, " + dishesByRestaurant.size() + " restaurant(s), " + payment.getMethod());
        return queries.groupViews(List.of(group)).get(0);
    }

    private void notifyNewOrder(Restaurant restaurant, FoodOrder order) {
        notifications.notify(restaurant.getOwnerId(), "NEW_ORDER", "Nouvelle commande",
                "Commande n°" + order.getId() + " de " + order.getSubtotal() + " FCFA a accepter.", "/restaurant/commandes");
    }

    /** Apres un paiement en ligne : les sous-commandes en attente partent chez les restaurants, ou sont annulees si le paiement echoue. */
    @EventListener
    @Transactional
    public void onPaymentCompleted(PaymentCompleted event) {
        for (FoodOrder order : orders.findByGroupIdOrderByIdAsc(event.groupId())) {
            if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
                continue;
            }
            if (event.success()) {
                orderStatus.transition(order, OrderStatus.PENDING, order.getCustomerId(), "Paiement recu");
                restaurants.findById(order.getRestaurantId()).ifPresent(r -> notifyNewOrder(r, order));
            } else {
                orderStatus.transition(order, OrderStatus.CANCELLED, order.getCustomerId(), "Paiement echoue");
            }
        }
        groups.findById(event.groupId()).ifPresent(group -> notifications.notify(group.getCustomerId(),
                event.success() ? "PAYMENT_SUCCEEDED" : "PAYMENT_FAILED",
                event.success() ? "Paiement recu" : "Paiement echoue",
                event.success() ? "Votre paiement de " + group.getTotal() + " FCFA est confirme."
                        : "Votre paiement n'a pas abouti. La commande est annulee.",
                "/commandes/" + group.getId()));
    }

    /** Ecran de test : le client valide ou refuse lui-meme un paiement du prestataire de test. */
    @Transactional
    public GroupView completeSandboxPayment(Long groupId, AuthUser customer, boolean success) {
        OrderGroup group = groups.findById(groupId).filter(g -> g.getCustomerId().equals(customer.id()))
                .orElseThrow(() -> ApiException.notFound("Commande introuvable."));
        Payment payment = payments.require(groupId);
        if (!props.isPaymentsSandboxEnabled() || !"sandbox".equals(payment.getProvider())) {
            throw ApiException.forbidden("Ce paiement ne passe pas par le prestataire de test.");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.conflict("PAYMENT_CLOSED", "Ce paiement est deja cloture.");
        }
        if (success) {
            payments.markSucceeded(payment, customer.id(), "Paiement de test valide");
        } else {
            payments.markFailed(payment, customer.id(), "Paiement de test refuse");
        }
        return queries.groupViews(List.of(group)).get(0);
    }

    // =====================================================================
    // Actions du restaurant
    // =====================================================================

    private FoodOrder ownedByRestaurant(Long orderId, AuthUser owner) {
        Restaurant restaurant = restaurantService.requireOwned(owner.id());
        return orders.lockById(orderId)
                .filter(o -> o.getRestaurantId().equals(restaurant.getId()) && o.getStatus() != OrderStatus.AWAITING_PAYMENT)
                .orElseThrow(() -> ApiException.notFound("Commande introuvable."));
    }

    @Transactional
    public void accept(Long orderId, AuthUser owner) {
        FoodOrder order = ownedByRestaurant(orderId, owner);
        orderStatus.transition(order, OrderStatus.ACCEPTED, owner.id(), "Acceptee par le restaurant");
        notifications.notify(order.getCustomerId(), "ORDER_ACCEPTED", "Commande acceptee",
                "Le restaurant prepare votre commande n°" + order.getId() + ".", "/commandes/" + order.getGroupId());
    }

    @Transactional
    public void reject(Long orderId, AuthUser owner, String reason) {
        FoodOrder order = ownedByRestaurant(orderId, owner);
        order.setRejectReason(reason);
        orderStatus.transition(order, OrderStatus.REJECTED, owner.id(), reason);
        settleAfterClosure(order, owner.id(), "Commande " + order.getId() + " refusee par le restaurant");
        notifications.notify(order.getCustomerId(), "ORDER_REJECTED", "Commande refusee",
                "Le restaurant ne peut pas preparer votre commande n°" + order.getId() + " : " + reason, "/commandes/" + order.getGroupId());
    }

    /** Le repas est pret : le systeme cherche aussitot un compartiment. */
    @Transactional
    public boolean ready(Long orderId, AuthUser owner) {
        FoodOrder order = ownedByRestaurant(orderId, owner);
        orderStatus.transition(order, OrderStatus.READY, owner.id(), "Repas pret");
        notifications.notify(order.getCustomerId(), "ORDER_READY", "Commande prete",
                "Votre commande n°" + order.getId() + " est prete et attend son compartiment.", "/commandes/" + order.getGroupId());
        return boxService.reserveForOrder(orderId, owner.id());
    }

    /** Nouvelle recherche de compartiment pour une commande prete restee sans compartiment. */
    @Transactional
    public boolean assignCompartment(Long orderId, AuthUser owner) {
        ownedByRestaurant(orderId, owner);
        return boxService.reserveForOrder(orderId, owner.id());
    }

    // =====================================================================
    // Annulation par le client
    // =====================================================================

    @Transactional
    public void cancelByCustomer(Long orderId, AuthUser customer) {
        FoodOrder order = orders.lockById(orderId).filter(o -> o.getCustomerId().equals(customer.id()))
                .orElseThrow(() -> ApiException.notFound("Commande introuvable."));
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw ApiException.conflict("TOO_LATE", "Le restaurant a deja pris en charge cette commande : elle ne peut plus etre annulee.");
        }
        boolean restaurantInformed = order.getStatus() == OrderStatus.PENDING;
        orderStatus.transition(order, OrderStatus.CANCELLED, customer.id(), "Annulee par le client");
        settleAfterClosure(order, customer.id(), "Commande " + order.getId() + " annulee par le client");
        if (restaurantInformed) {
            restaurants.findById(order.getRestaurantId()).ifPresent(r -> notifications.notify(r.getOwnerId(), "ORDER_CANCELLED",
                    "Commande annulee", "Le client a annule la commande n°" + order.getId() + ".", "/restaurant/commandes"));
        }
    }

    /**
     * Regle le paiement apres le refus ou l'annulation d'une sous-commande :
     * remboursement de sa part si le paiement est encaisse ; si toute la commande globale est close,
     * remboursement integral (frais de livraison compris) ou annulation du paiement en attente.
     */
    private void settleAfterClosure(FoodOrder order, Long actorId, String reason) {
        boolean allDead = orders.findByGroupIdOrderByIdAsc(order.getGroupId()).stream().allMatch(o -> DEAD.contains(o.getStatus()));
        Payment payment = payments.require(order.getGroupId());
        if (allDead) {
            payments.cancelPending(order.getGroupId(), actorId, reason);
            payments.refund(order.getGroupId(), payment.getAmount(), actorId, reason);
        } else {
            payments.reducePending(order.getGroupId(), order.getSubtotal(), actorId, reason);
            payments.refund(order.getGroupId(), order.getSubtotal(), actorId, reason);
        }
    }
}
