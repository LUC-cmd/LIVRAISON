package tg.smartfood.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tg.smartfood.audit.AuditService;
import tg.smartfood.audit.SecurityEvent;
import tg.smartfood.audit.SecurityEventRepository;
import tg.smartfood.auth.AuthService;
import tg.smartfood.box.BoxService;
import tg.smartfood.box.BoxStatus;
import tg.smartfood.box.BoxViews.BoxView;
import tg.smartfood.box.BoxViews.CompartmentView;
import tg.smartfood.box.BoxViews.CreatedBox;
import tg.smartfood.box.CompartmentRepository;
import tg.smartfood.box.CompartmentState;
import tg.smartfood.box.DeliveryBox;
import tg.smartfood.box.DeliveryBoxRepository;
import tg.smartfood.box.OpeningEvent;
import tg.smartfood.box.OpeningEventRepository;
import tg.smartfood.common.ApiException;
import tg.smartfood.common.AuthUser;
import tg.smartfood.delivery.Delivery;
import tg.smartfood.delivery.DeliveryRepository;
import tg.smartfood.delivery.DeliveryService;
import tg.smartfood.iot.IotDeviceRepository;
import tg.smartfood.iot.SensorEvent;
import tg.smartfood.iot.SensorEventRepository;
import tg.smartfood.notification.NotificationService;
import tg.smartfood.order.FoodOrder;
import tg.smartfood.order.FoodOrderRepository;
import tg.smartfood.order.OrderGroup;
import tg.smartfood.order.OrderGroupRepository;
import tg.smartfood.order.OrderQueryService;
import tg.smartfood.order.OrderStatus;
import tg.smartfood.order.OrderViews.GroupView;
import tg.smartfood.payment.Payment;
import tg.smartfood.payment.PaymentRepository;
import tg.smartfood.payment.PaymentStatus;
import tg.smartfood.payment.PaymentTransaction;
import tg.smartfood.payment.PaymentTransactionRepository;
import tg.smartfood.restaurant.Restaurant;
import tg.smartfood.restaurant.RestaurantRepository;
import tg.smartfood.restaurant.RestaurantService;
import tg.smartfood.restaurant.RestaurantStatus;
import tg.smartfood.restaurant.SizeClass;
import tg.smartfood.support.Complaint;
import tg.smartfood.support.ComplaintRepository;
import tg.smartfood.support.ComplaintStatus;
import tg.smartfood.user.CourierProfile;
import tg.smartfood.user.CourierProfileRepository;
import tg.smartfood.user.Role;
import tg.smartfood.user.User;
import tg.smartfood.user.UserDto;
import tg.smartfood.user.UserRepository;
import tg.smartfood.user.UserStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Espace administrateur : pilotage de toute la plateforme. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Set<OrderStatus> CLOSED = EnumSet.of(OrderStatus.COLLECTED, OrderStatus.REJECTED, OrderStatus.CANCELLED);

    public record DayStat(LocalDate day, int orders, int revenue) {
    }

    public record Dashboard(int customers, int restaurants, int couriers, long restaurantsPending, int ordersTotal, int ordersInProgress,
                            int ordersCollected, int grossSales, int commissions, int deliveryFees, long activeDeliveries, int boxes,
                            long boxesOffline, Map<CompartmentState, Long> compartments, long openComplaints, List<DayStat> lastDays) {
    }

    public record RestaurantRow(Restaurant restaurant, String ownerName, String ownerEmail) {
    }

    public record CourierRow(UserDto user, boolean available, String vehicle, Long boxId, String boxName) {
    }

    public record DeliveryRow(Delivery delivery, String courierName, String boxName) {
    }

    public record PaymentRow(Payment payment, List<PaymentTransaction> transactions) {
    }

    public record CreateBoxRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 80) String name,
            @NotBlank(message = "Le numero de serie est obligatoire.")
            @Pattern(regexp = "^[A-Za-z0-9-]{3,40}$", message = "Numero de serie : 3 a 40 lettres, chiffres ou tirets.") String serial,
            @NotEmpty(message = "Indiquez au moins un compartiment.") @Size(max = 10, message = "Dix compartiments au maximum.") List<@NotNull SizeClass> sizes,
            Long courierId) {
    }

    public record CourierAssignRequest(Long courierId) {
    }

    public record BoxStatusRequest(@NotNull(message = "Statut manquant.") BoxStatus status) {
    }

    public record ResetRequest(boolean mealDelivered) {
    }

    public record ReasonRequest(@NotBlank(message = "Indiquez le motif.") @Size(max = 300) String reason) {
    }

    public record ComplaintUpdate(@NotNull(message = "Statut manquant.") ComplaintStatus status, @Size(max = 2000) String response) {
    }

    public record BroadcastRequest(Role role,
                                   @NotBlank(message = "Le titre est obligatoire.") @Size(max = 160) String title,
                                   @NotBlank(message = "Le message est obligatoire.") @Size(max = 600) String body) {
    }

    private final UserRepository users;
    private final RestaurantRepository restaurants;
    private final CourierProfileRepository courierProfiles;
    private final FoodOrderRepository orders;
    private final OrderGroupRepository groups;
    private final OrderQueryService queries;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final DeliveryRepository deliveries;
    private final DeliveryBoxRepository boxes;
    private final CompartmentRepository compartments;
    private final IotDeviceRepository devices;
    private final OpeningEventRepository openingEvents;
    private final SensorEventRepository sensorEvents;
    private final SecurityEventRepository securityEvents;
    private final ComplaintRepository complaints;
    private final BoxService boxService;
    private final AuthService authService;
    private final NotificationService notifications;
    private final AuditService audit;

    public AdminController(UserRepository users, RestaurantRepository restaurants, CourierProfileRepository courierProfiles,
                           FoodOrderRepository orders, OrderGroupRepository groups, OrderQueryService queries, PaymentRepository payments,
                           PaymentTransactionRepository transactions, DeliveryRepository deliveries, DeliveryBoxRepository boxes,
                           CompartmentRepository compartments, IotDeviceRepository devices, OpeningEventRepository openingEvents,
                           SensorEventRepository sensorEvents, SecurityEventRepository securityEvents, ComplaintRepository complaints,
                           BoxService boxService, AuthService authService, NotificationService notifications, AuditService audit) {
        this.users = users;
        this.restaurants = restaurants;
        this.courierProfiles = courierProfiles;
        this.orders = orders;
        this.groups = groups;
        this.queries = queries;
        this.payments = payments;
        this.transactions = transactions;
        this.deliveries = deliveries;
        this.boxes = boxes;
        this.compartments = compartments;
        this.devices = devices;
        this.openingEvents = openingEvents;
        this.sensorEvents = sensorEvents;
        this.securityEvents = securityEvents;
        this.complaints = complaints;
        this.boxService = boxService;
        this.authService = authService;
        this.notifications = notifications;
        this.audit = audit;
    }

    // ---------- Tableau de bord ----------

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        List<FoodOrder> all = orders.findAll();
        List<FoodOrder> collected = all.stream().filter(o -> o.getStatus() == OrderStatus.COLLECTED).toList();
        int inProgress = (int) all.stream().filter(o -> !CLOSED.contains(o.getStatus())).count();
        int grossSales = collected.stream().mapToInt(FoodOrder::getSubtotal).sum();
        int commissions = collected.stream().mapToInt(FoodOrder::getCommission).sum();
        int deliveryFees = 0;
        for (Payment p : payments.findAll()) {
            if (p.getStatus() == PaymentStatus.SUCCEEDED) {
                deliveryFees += groups.findById(p.getGroupId()).map(OrderGroup::getDeliveryFee).orElse(0);
            }
        }
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
        byDay.forEach((day, values) -> days.add(new DayStat(day, values[0], values[1])));
        Map<CompartmentState, Long> states = new LinkedHashMap<>();
        for (CompartmentState state : CompartmentState.values()) {
            states.put(state, compartments.countByState(state));
        }
        return new Dashboard(users.findByRole(Role.CUSTOMER).size(), (int) restaurants.count(), users.findByRole(Role.COURIER).size(),
                restaurants.countByStatus(RestaurantStatus.PENDING), all.size(), inProgress, collected.size(), grossSales, commissions,
                deliveryFees, deliveries.countByStatusIn(DeliveryService.ACTIVE), (int) boxes.count(), devices.countByOnlineFalse(),
                states, complaints.countByStatus(ComplaintStatus.OPEN), days);
    }

    // ---------- Utilisateurs ----------

    @GetMapping("/users")
    public List<UserDto> users(@RequestParam(required = false) Role role) {
        List<User> list = role == null ? users.findAllByOrderByIdDesc() : users.findByRole(role);
        return list.stream().map(UserDto::of).toList();
    }

    @PostMapping("/users/{id}/suspend")
    @Transactional
    public UserDto suspend(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id) {
        return setUserStatus(admin, id, UserStatus.SUSPENDED);
    }

    @PostMapping("/users/{id}/activate")
    @Transactional
    public UserDto activate(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id) {
        return setUserStatus(admin, id, UserStatus.ACTIVE);
    }

    private UserDto setUserStatus(AuthUser admin, Long id, UserStatus status) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("Compte introuvable."));
        if (user.getId().equals(admin.id())) {
            throw ApiException.badRequest("SELF", "Vous ne pouvez pas modifier votre propre compte ici.");
        }
        boolean targetIsStaff = user.getRoles().contains(Role.ADMIN) || user.getRoles().contains(Role.SUPERADMIN);
        if (targetIsStaff && !admin.has(Role.SUPERADMIN)) {
            throw ApiException.forbidden("Seul un superadministrateur peut suspendre un administrateur.");
        }
        if (user.getRoles().contains(Role.SUPERADMIN)) {
            throw ApiException.forbidden("Un superadministrateur ne peut pas etre suspendu.");
        }
        user.setStatus(status);
        users.save(user);
        if (status == UserStatus.SUSPENDED) {
            authService.revokeAll(user.getId());
        }
        audit.log(admin.id(), "USER_" + status.name(), "USER", user.getId(), user.getEmail());
        return UserDto.of(user);
    }

    // ---------- Restaurants ----------

    @GetMapping("/restaurants")
    public List<RestaurantRow> restaurants() {
        return restaurants.findAllByOrderByIdDesc().stream().map(r -> {
            User owner = users.findById(r.getOwnerId()).orElse(null);
            return new RestaurantRow(r, owner == null ? "" : owner.getFullName(), owner == null ? "" : owner.getEmail());
        }).toList();
    }

    @PostMapping("/restaurants/{id}/approve")
    @Transactional
    public Restaurant approve(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id) {
        return setRestaurantStatus(admin, id, RestaurantStatus.APPROVED, "Votre restaurant est valide : il est maintenant visible des clients.");
    }

    @PostMapping("/restaurants/{id}/suspend")
    @Transactional
    public Restaurant suspendRestaurant(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id) {
        return setRestaurantStatus(admin, id, RestaurantStatus.SUSPENDED, "Votre restaurant est suspendu. Contactez l'assistance.");
    }

    private Restaurant setRestaurantStatus(AuthUser admin, Long id, RestaurantStatus status, String message) {
        Restaurant restaurant = restaurants.findById(id).orElseThrow(() -> ApiException.notFound("Restaurant introuvable."));
        restaurant.setStatus(status);
        restaurants.save(restaurant);
        audit.log(admin.id(), "RESTAURANT_" + status.name(), "RESTAURANT", id, restaurant.getName());
        notifications.notify(restaurant.getOwnerId(), "RESTAURANT_STATUS", "Statut de votre restaurant", message, "/restaurant");
        return restaurant;
    }

    // ---------- Livreurs ----------

    @GetMapping("/couriers")
    public List<CourierRow> couriers() {
        return users.findByRole(Role.COURIER).stream().map(u -> {
            CourierProfile profile = courierProfiles.findById(u.getId()).orElse(null);
            DeliveryBox box = boxes.findByCourierId(u.getId()).orElse(null);
            return new CourierRow(UserDto.of(u), profile != null && profile.isAvailable(), profile == null ? "" : profile.getVehicle(),
                    box == null ? null : box.getId(), box == null ? null : box.getName());
        }).toList();
    }

    // ---------- Commandes, paiements, livraisons ----------

    @GetMapping("/orders")
    public List<GroupView> orders() {
        return queries.groupViews(groups.findTop200ByOrderByIdDesc());
    }

    @GetMapping("/payments")
    public List<PaymentRow> payments() {
        return payments.findTop200ByOrderByIdDesc().stream()
                .map(p -> new PaymentRow(p, transactions.findByPaymentIdOrderByIdAsc(p.getId())))
                .toList();
    }

    @GetMapping("/deliveries")
    public List<DeliveryRow> deliveries() {
        Map<Long, String> courierNames = new HashMap<>();
        Map<Long, String> boxNames = new HashMap<>();
        return deliveries.findTop200ByOrderByIdDesc().stream().map(d -> new DeliveryRow(d,
                courierNames.computeIfAbsent(d.getCourierId(), id -> users.findById(id).map(User::getFullName).orElse("")),
                boxNames.computeIfAbsent(d.getBoxId(), id -> boxes.findById(id).map(DeliveryBox::getName).orElse("")))).toList();
    }

    @PostMapping("/deliveries/{id}/cancel")
    public List<DeliveryRow> cancelDelivery(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        boxService.cancelDelivery(id, admin.id(), request.reason().trim());
        return deliveries();
    }

    // ---------- Coffres ----------

    @PostMapping("/boxes")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedBox createBox(@AuthenticationPrincipal AuthUser admin, @Valid @RequestBody CreateBoxRequest request) {
        return boxService.createBox(request.name().trim(), request.serial().trim().toUpperCase(), request.sizes(), request.courierId(), admin);
    }

    @PutMapping("/boxes/{id}/courier")
    public BoxView assignCourier(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id, @RequestBody CourierAssignRequest request) {
        return boxService.assignCourier(id, request.courierId(), admin);
    }

    @PutMapping("/boxes/{id}/status")
    public BoxView boxStatus(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id, @Valid @RequestBody BoxStatusRequest request) {
        return boxService.setStatus(id, request.status(), admin);
    }

    @PostMapping("/boxes/{id}/unlock-keypad")
    public BoxView unlockKeypad(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id) {
        return boxService.unlockKeypad(id, admin);
    }

    @PostMapping("/compartments/{id}/reset")
    public CompartmentView resetCompartment(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id, @RequestBody ResetRequest request) {
        return boxService.resetCompartment(id, request.mealDelivered(), admin);
    }

    // ---------- Evenements et incidents ----------

    @GetMapping("/events/openings")
    public List<OpeningEvent> openingEvents(@RequestParam(required = false) Long boxId) {
        return boxId == null ? openingEvents.findTop200ByOrderByIdDesc() : openingEvents.findTop100ByBoxIdOrderByIdDesc(boxId);
    }

    @GetMapping("/events/sensors")
    public List<SensorEvent> sensorEvents() {
        return sensorEvents.findTop200ByOrderByIdDesc();
    }

    @GetMapping("/events/security")
    public List<SecurityEvent> securityEvents() {
        return securityEvents.findTop300ByOrderByIdDesc();
    }

    // ---------- Reclamations et notifications ----------

    @GetMapping("/complaints")
    public List<Complaint> complaints() {
        return complaints.findAllByOrderByIdDesc();
    }

    @PutMapping("/complaints/{id}")
    @Transactional
    public Complaint updateComplaint(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id, @Valid @RequestBody ComplaintUpdate request) {
        Complaint complaint = complaints.findById(id).orElseThrow(() -> ApiException.notFound("Reclamation introuvable."));
        complaint.setStatus(request.status());
        complaint.setResponse(request.response() == null || request.response().isBlank() ? null : request.response().trim());
        Complaint saved = complaints.save(complaint);
        audit.log(admin.id(), "COMPLAINT_" + request.status().name(), "COMPLAINT", id, null);
        notifications.notify(saved.getUserId(), "COMPLAINT_UPDATE", "Votre reclamation",
                saved.getResponse() == null ? "Votre reclamation « " + saved.getSubject() + " » est passee au statut " + saved.getStatus() + "."
                        : saved.getResponse(), "/reclamations");
        return saved;
    }

    @PostMapping("/notifications/broadcast")
    @Transactional
    public Map<String, Object> broadcast(@AuthenticationPrincipal AuthUser admin, @Valid @RequestBody BroadcastRequest request) {
        List<User> targets = request.role() == null ? users.findAllByOrderByIdDesc() : users.findByRole(request.role());
        List<Long> ids = targets.stream().filter(u -> u.getStatus() == UserStatus.ACTIVE).map(User::getId).toList();
        notifications.notifyAll(ids, "ANNOUNCEMENT", request.title().trim(), request.body().trim(), null);
        audit.log(admin.id(), "BROADCAST", null, null, request.title() + " -> " + ids.size() + " destinataire(s)");
        return Map.of("recipients", ids.size());
    }
}
