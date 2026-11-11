package tg.smartfood.restaurant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tg.smartfood.box.BoxService;
import tg.smartfood.box.BoxViews.CompartmentView;
import tg.smartfood.common.AuthUser;
import tg.smartfood.order.FoodOrder;
import tg.smartfood.order.FoodOrderRepository;
import tg.smartfood.order.OrderItem;
import tg.smartfood.order.OrderItemRepository;
import tg.smartfood.order.OrderQueryService;
import tg.smartfood.order.OrderService;
import tg.smartfood.order.OrderStatus;
import tg.smartfood.order.OrderViews.RestaurantOrderView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Espace restaurant : commandes recues, preparation, chargement dans le coffre et statistiques. */
@RestController
@RequestMapping("/api/v1/restaurant")
public class RestaurantOrderController {

    public record RejectRequest(@NotBlank(message = "Indiquez le motif du refus.") @Size(max = 300) String reason) {
    }

    public record ReadyResult(boolean compartmentReserved, String message) {
    }

    public record DayStat(LocalDate day, int orders, int revenue) {
    }

    public record DishStat(String name, int quantity, int revenue) {
    }

    public record Stats(int totalOrders, int collectedOrders, int inProgressOrders, int rejectedOrders, int revenue, int commission,
                        int net, List<DayStat> lastDays, List<DishStat> topDishes) {
    }

    private final RestaurantService restaurantService;
    private final OrderService orderService;
    private final OrderQueryService queries;
    private final BoxService boxService;
    private final FoodOrderRepository orders;
    private final OrderItemRepository items;

    public RestaurantOrderController(RestaurantService restaurantService, OrderService orderService, OrderQueryService queries,
                                     BoxService boxService, FoodOrderRepository orders, OrderItemRepository items) {
        this.restaurantService = restaurantService;
        this.orderService = orderService;
        this.queries = queries;
        this.boxService = boxService;
        this.orders = orders;
        this.items = items;
    }

    @GetMapping("/orders")
    public List<RestaurantOrderView> list(@AuthenticationPrincipal AuthUser user) {
        return queries.ordersForRestaurant(restaurantService.requireOwned(user.id()).getId());
    }

    @PostMapping("/orders/{id}/accept")
    public List<RestaurantOrderView> accept(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        orderService.accept(id, user);
        return list(user);
    }

    @PostMapping("/orders/{id}/reject")
    public List<RestaurantOrderView> reject(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        orderService.reject(id, user, request.reason().trim());
        return list(user);
    }

    @PostMapping("/orders/{id}/ready")
    public ReadyResult ready(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return readyResult(orderService.ready(id, user));
    }

    @PostMapping("/orders/{id}/assign-compartment")
    public ReadyResult assign(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return readyResult(orderService.assignCompartment(id, user));
    }

    private static ReadyResult readyResult(boolean reserved) {
        return new ReadyResult(reserved, reserved
                ? "Un compartiment est reserve. Demandez son ouverture pour charger le repas."
                : "Aucun compartiment n'est disponible pour l'instant. Reessayez dans quelques minutes.");
    }

    /** Autorisation temporaire de chargement : ouvre uniquement le compartiment attribue a cette commande. */
    @PostMapping("/orders/{id}/loading-authorization")
    public CompartmentView authorizeLoading(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return boxService.authorizeLoading(id, user);
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public Stats stats(@AuthenticationPrincipal AuthUser user) {
        Long restaurantId = restaurantService.requireOwned(user.id()).getId();
        List<FoodOrder> all = orders.findByRestaurantIdOrderByIdDesc(restaurantId).stream()
                .filter(o -> o.getStatus() != OrderStatus.AWAITING_PAYMENT).toList();
        List<FoodOrder> collected = all.stream().filter(o -> o.getStatus() == OrderStatus.COLLECTED).toList();
        int rejected = (int) all.stream().filter(o -> o.getStatus() == OrderStatus.REJECTED || o.getStatus() == OrderStatus.CANCELLED).count();
        int revenue = collected.stream().mapToInt(FoodOrder::getSubtotal).sum();
        int commission = collected.stream().mapToInt(FoodOrder::getCommission).sum();

        LocalDate today = LocalDate.now(RestaurantService.ZONE);
        Map<LocalDate, int[]> byDay = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            byDay.put(today.minusDays(i), new int[2]);
        }
        for (FoodOrder o : collected) {
            int[] bucket = byDay.get(o.getUpdatedAt().atZone(RestaurantService.ZONE).toLocalDate());
            if (bucket != null) {
                bucket[0]++;
                bucket[1] += o.getSubtotal();
            }
        }
        List<DayStat> days = new ArrayList<>();
        byDay.forEach((day, bucket) -> days.add(new DayStat(day, bucket[0], bucket[1])));

        Map<String, int[]> byDish = new LinkedHashMap<>();
        if (!collected.isEmpty()) {
            for (OrderItem item : items.findByOrderIdIn(collected.stream().map(FoodOrder::getId).toList())) {
                int[] bucket = byDish.computeIfAbsent(item.getDishName(), k -> new int[2]);
                bucket[0] += item.getQuantity();
                bucket[1] += item.getQuantity() * item.getUnitPrice();
            }
        }
        List<DishStat> top = byDish.entrySet().stream()
                .map(e -> new DishStat(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingInt(DishStat::quantity).reversed())
                .limit(5)
                .toList();
        return new Stats(all.size(), collected.size(), all.size() - collected.size() - rejected, rejected, revenue, commission,
                revenue - commission, days, top);
    }
}
