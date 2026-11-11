package tg.smartfood.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tg.smartfood.box.BoxService;
import tg.smartfood.box.CodeService;
import tg.smartfood.box.CodeService.RevealedCode;
import tg.smartfood.common.ApiException;
import tg.smartfood.common.AuthUser;
import tg.smartfood.delivery.DeliveryService;
import tg.smartfood.delivery.DeliveryViews.TrackingView;
import tg.smartfood.notification.NotificationService;
import tg.smartfood.order.FoodOrder;
import tg.smartfood.order.FoodOrderRepository;
import tg.smartfood.order.OrderQueryService;
import tg.smartfood.order.OrderService;
import tg.smartfood.order.OrderViews.CheckoutRequest;
import tg.smartfood.order.OrderViews.GroupView;
import tg.smartfood.order.OrderViews.OrderView;
import tg.smartfood.support.Complaint;
import tg.smartfood.support.ComplaintRepository;

import java.time.Instant;
import java.util.List;

/** Espace client : commandes, suivi, code de retrait, reclamations. */
@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {

    public record SandboxRequest(boolean success) {
    }

    public record RetryPaymentRequest(@jakarta.validation.constraints.NotNull(message = "Choisissez un moyen de paiement.") tg.smartfood.payment.PaymentMethod paymentMethod) {
    }

    public record ComplaintRequest(Long orderId,
                                   @NotBlank(message = "L'objet est obligatoire.") @Size(max = 160) String subject,
                                   @NotBlank(message = "Decrivez le probleme.") @Size(max = 2000) String message) {
    }

    /** Code de retrait, visible uniquement par le client proprietaire de la commande. */
    public record CodeView(String code, Instant expiresAt, Long boxId, String boxName, Integer compartmentNumber) {
    }

    private final OrderService orderService;
    private final OrderQueryService queries;
    private final FoodOrderRepository orders;
    private final CodeService codeService;
    private final BoxService boxService;
    private final DeliveryService deliveryService;
    private final ComplaintRepository complaints;
    private final NotificationService notifications;

    public CustomerController(OrderService orderService, OrderQueryService queries, FoodOrderRepository orders, CodeService codeService,
                              BoxService boxService, DeliveryService deliveryService, ComplaintRepository complaints,
                              NotificationService notifications) {
        this.orderService = orderService;
        this.queries = queries;
        this.orders = orders;
        this.codeService = codeService;
        this.boxService = boxService;
        this.deliveryService = deliveryService;
        this.complaints = complaints;
        this.notifications = notifications;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupView checkout(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CheckoutRequest request,
                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return orderService.checkout(user, request, idempotencyKey);
    }

    @GetMapping("/orders")
    public List<GroupView> list(@AuthenticationPrincipal AuthUser user) {
        return queries.groupsForCustomer(user.id());
    }

    @GetMapping("/orders/{groupId}")
    public GroupView get(@AuthenticationPrincipal AuthUser user, @PathVariable Long groupId) {
        return queries.groupForCustomer(groupId, user.id());
    }

    @GetMapping("/orders/{groupId}/tracking")
    public List<TrackingView> tracking(@AuthenticationPrincipal AuthUser user, @PathVariable Long groupId) {
        return deliveryService.tracking(groupId, user.id());
    }

    @PostMapping("/orders/{groupId}/sandbox-payment")
    public GroupView sandboxPayment(@AuthenticationPrincipal AuthUser user, @PathVariable Long groupId, @RequestBody SandboxRequest request) {
        return orderService.completeSandboxPayment(groupId, user, request.success());
    }

    @PostMapping("/orders/{groupId}/retry-payment")
    public GroupView retryPayment(@AuthenticationPrincipal AuthUser user, @PathVariable Long groupId, @Valid @RequestBody RetryPaymentRequest request) {
        return orderService.retryPayment(groupId, user, request.paymentMethod());
    }

    @PostMapping("/order-items/{orderId}/cancel")
    public GroupView cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long orderId) {
        orderService.cancelByCustomer(orderId, user);
        return queries.groupForCustomer(ownedOrder(orderId, user).getGroupId(), user.id());
    }

    @GetMapping("/order-items/{orderId}/code")
    public CodeView code(@AuthenticationPrincipal AuthUser user, @PathVariable Long orderId) {
        FoodOrder order = ownedOrder(orderId, user);
        RevealedCode revealed = codeService.reveal(order, user.id());
        OrderView view = queries.orderViews(List.of(order)).get(0);
        return new CodeView(revealed.code(), revealed.expiresAt(), view.boxId(), view.boxName(), view.compartmentNumber());
    }

    @PostMapping("/order-items/{orderId}/code/renew")
    public CodeView renewCode(@AuthenticationPrincipal AuthUser user, @PathVariable Long orderId) {
        boxService.renewCode(orderId, user.id());
        return code(user, orderId);
    }

    private FoodOrder ownedOrder(Long orderId, AuthUser user) {
        return orders.findById(orderId).filter(o -> o.getCustomerId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Commande introuvable."));
    }

    @GetMapping("/complaints")
    public List<Complaint> complaints(@AuthenticationPrincipal AuthUser user) {
        return complaints.findByUserIdOrderByIdDesc(user.id());
    }

    @PostMapping("/complaints")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Complaint createComplaint(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ComplaintRequest request) {
        if (request.orderId() != null) {
            ownedOrder(request.orderId(), user);
        }
        Complaint complaint = new Complaint();
        complaint.setUserId(user.id());
        complaint.setOrderId(request.orderId());
        complaint.setSubject(request.subject().trim());
        complaint.setMessage(request.message().trim());
        Complaint saved = complaints.save(complaint);
        notifications.notifyAdmins("COMPLAINT", "Nouvelle reclamation", saved.getSubject(), "/admin/reclamations");
        return saved;
    }
}
