package tg.smartfood.box;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.audit.AuditService;
import tg.smartfood.box.BoxViews.BoxView;
import tg.smartfood.box.BoxViews.CompartmentView;
import tg.smartfood.box.BoxViews.CreatedBox;
import tg.smartfood.box.BoxViews.DeviceView;
import tg.smartfood.common.ApiException;
import tg.smartfood.common.AppProps;
import tg.smartfood.common.AuthUser;
import tg.smartfood.common.Hashing;
import tg.smartfood.delivery.Delivery;
import tg.smartfood.delivery.DeliveryRepository;
import tg.smartfood.delivery.DeliveryService;
import tg.smartfood.delivery.DeliveryStatus;
import tg.smartfood.iot.DeviceEvents;
import tg.smartfood.iot.DeviceGateway;
import tg.smartfood.iot.DeviceGateway.UnlockResult;
import tg.smartfood.iot.DeviceHeartbeat;
import tg.smartfood.iot.DeviceHeartbeatRepository;
import tg.smartfood.iot.DeviceKind;
import tg.smartfood.iot.IotDevice;
import tg.smartfood.iot.IotDeviceRepository;
import tg.smartfood.iot.SensorEvent;
import tg.smartfood.iot.SensorEventRepository;
import tg.smartfood.iot.SimCompartmentHw;
import tg.smartfood.iot.SimCompartmentHwRepository;
import tg.smartfood.notification.NotificationService;
import tg.smartfood.order.FoodOrder;
import tg.smartfood.order.FoodOrderRepository;
import tg.smartfood.order.OrderGroup;
import tg.smartfood.order.OrderGroupRepository;
import tg.smartfood.order.OrderStatus;
import tg.smartfood.order.OrderStatusService;
import tg.smartfood.payment.Payment;
import tg.smartfood.payment.PaymentRepository;
import tg.smartfood.payment.PaymentService;
import tg.smartfood.payment.PaymentStatus;
import tg.smartfood.realtime.RealtimeService;
import tg.smartfood.restaurant.Restaurant;
import tg.smartfood.restaurant.RestaurantRepository;
import tg.smartfood.restaurant.SizeClass;
import tg.smartfood.user.CourierProfile;
import tg.smartfood.user.CourierProfileRepository;
import tg.smartfood.user.Role;
import tg.smartfood.user.User;
import tg.smartfood.user.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coeur du coffre intelligent : reservation transactionnelle des compartiments, autorisations de chargement,
 * verification des codes au clavier et reaction aux evenements du materiel. Aucune ouverture n'a lieu sans decision prise ici.
 */
@Service
public class BoxService {

    private static final Logger log = LoggerFactory.getLogger(BoxService.class);
    private static final Set<OrderStatus> CARRIED = EnumSet.of(OrderStatus.LOADED, OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED);
    private static final int LOW_BATTERY = 15;
    private static final double HIGH_TEMPERATURE = 50;

    private final DeliveryBoxRepository boxes;
    private final CompartmentRepository compartments;
    private final CompartmentAssignmentRepository assignments;
    private final OpeningAuthorizationRepository authorizations;
    private final WithdrawalCodeRepository codes;
    private final OpeningEventRepository openingEvents;
    private final IotDeviceRepository devices;
    private final SensorEventRepository sensorEvents;
    private final DeviceHeartbeatRepository heartbeats;
    private final SimCompartmentHwRepository simHardware;
    private final FoodOrderRepository orders;
    private final OrderGroupRepository groups;
    private final RestaurantRepository restaurants;
    private final CourierProfileRepository courierProfiles;
    private final UserRepository users;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final DeliveryRepository deliveries;
    private final DeliveryService deliveryService;
    private final OrderStatusService orderStatus;
    private final CodeService codeService;
    private final DeviceGateway gateway;
    private final NotificationService notifications;
    private final RealtimeService realtime;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final AppProps props;

    public BoxService(DeliveryBoxRepository boxes, CompartmentRepository compartments, CompartmentAssignmentRepository assignments,
                      OpeningAuthorizationRepository authorizations, WithdrawalCodeRepository codes, OpeningEventRepository openingEvents,
                      IotDeviceRepository devices, SensorEventRepository sensorEvents, DeviceHeartbeatRepository heartbeats,
                      SimCompartmentHwRepository simHardware, FoodOrderRepository orders, OrderGroupRepository groups,
                      RestaurantRepository restaurants, CourierProfileRepository courierProfiles, UserRepository users,
                      PaymentRepository payments, PaymentService paymentService, DeliveryRepository deliveries,
                      DeliveryService deliveryService, OrderStatusService orderStatus, CodeService codeService, DeviceGateway gateway,
                      NotificationService notifications, RealtimeService realtime, AuditService audit,
                      PasswordEncoder passwordEncoder, AppProps props) {
        this.boxes = boxes;
        this.compartments = compartments;
        this.assignments = assignments;
        this.authorizations = authorizations;
        this.codes = codes;
        this.openingEvents = openingEvents;
        this.devices = devices;
        this.sensorEvents = sensorEvents;
        this.heartbeats = heartbeats;
        this.simHardware = simHardware;
        this.orders = orders;
        this.groups = groups;
        this.restaurants = restaurants;
        this.courierProfiles = courierProfiles;
        this.users = users;
        this.payments = payments;
        this.paymentService = paymentService;
        this.deliveries = deliveries;
        this.deliveryService = deliveryService;
        this.orderStatus = orderStatus;
        this.codeService = codeService;
        this.gateway = gateway;
        this.notifications = notifications;
        this.realtime = realtime;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    // =====================================================================
    // Reservation
    // =====================================================================

    /** Un coffre est utilisable s'il est en service, connecte, et porte par un livreur disponible. */
    private boolean usable(DeliveryBox box) {
        if (box.getStatus() != BoxStatus.ACTIVE || box.getCourierId() == null) {
            return false;
        }
        boolean online = devices.findByBoxId(box.getId()).map(IotDevice::isOnline).orElse(false);
        boolean courierAvailable = courierProfiles.findById(box.getCourierId()).map(CourierProfile::isAvailable).orElse(false);
        return online && courierAvailable;
    }

    /**
     * Cherche et reserve un compartiment pour une sous-commande prete.
     * Priorite au coffre qui porte deja la livraison de la meme commande globale ; sinon premier coffre utilisable
     * dont le livreur n'est pas deja en route. Le compartiment est verrouille en base (FOR UPDATE SKIP LOCKED) :
     * deux reservations simultanees ne peuvent pas obtenir le meme.
     *
     * @return true si un compartiment a ete reserve, false si aucun n'est disponible pour l'instant
     */
    @Transactional
    public boolean reserveForOrder(Long orderId, Long actorId) {
        FoodOrder order = orders.lockById(orderId).orElseThrow(() -> ApiException.notFound("Commande introuvable."));
        if (order.getStatus() != OrderStatus.READY) {
            throw ApiException.conflict("NOT_READY", "Seule une commande prete peut recevoir un compartiment.");
        }
        OrderGroup group = groups.findById(order.getGroupId()).orElseThrow(() -> ApiException.notFound("Commande introuvable."));
        int minRank = order.getRequiredSize().rank();

        Delivery existing = deliveries.findFirstByGroupIdAndStatusInOrderByIdAsc(group.getId(), DeliveryService.OPEN_FOR_LOADING).orElse(null);
        DeliveryBox chosenBox = null;
        Compartment chosen = null;
        if (existing != null) {
            DeliveryBox box = boxes.findById(existing.getBoxId()).orElse(null);
            if (box != null && usable(box)) {
                chosen = compartments.lockFirstAvailable(box.getId(), minRank).orElse(null);
                if (chosen != null) {
                    chosenBox = box;
                }
            }
        }
        if (chosen == null) {
            for (DeliveryBox box : boxes.findByStatusOrderByIdAsc(BoxStatus.ACTIVE)) {
                if (existing != null && box.getId().equals(existing.getBoxId())) {
                    continue;
                }
                if (!usable(box) || deliveries.existsByCourierIdAndStatusIn(box.getCourierId(), DeliveryService.ON_THE_ROAD)) {
                    continue;
                }
                chosen = compartments.lockFirstAvailable(box.getId(), minRank).orElse(null);
                if (chosen != null) {
                    chosenBox = box;
                    break;
                }
            }
        }
        Restaurant restaurant = restaurants.findById(order.getRestaurantId()).orElseThrow(() -> ApiException.notFound("Restaurant introuvable."));
        if (chosen == null) {
            notifications.notify(restaurant.getOwnerId(), "NO_COMPARTMENT", "Aucun compartiment disponible",
                    "La commande n°" + order.getId() + " attend un compartiment. Reessayez dans quelques minutes.", "/restaurant/commandes");
            return false;
        }

        CompartmentAssignment assignment = new CompartmentAssignment();
        assignment.setCompartmentId(chosen.getId());
        assignment.setOrderId(order.getId());
        assignments.save(assignment);

        chosen.setState(CompartmentState.RESERVED);
        touch(chosen);

        Delivery delivery = existing != null && existing.getBoxId().equals(chosenBox.getId())
                ? existing : deliveryService.create(group, chosenBox);
        order.setDeliveryId(delivery.getId());
        orderStatus.transition(order, OrderStatus.RESERVED, actorId,
                "Compartiment " + chosen.getNumber() + " du coffre " + chosenBox.getName());
        event(chosen.getBoxId(), chosen.getId(), order.getId(), actorId, "COMPARTMENT_RESERVED", true, null);
        notifications.notify(restaurant.getOwnerId(), "COMPARTMENT_RESERVED", "Compartiment reserve",
                "Commande n°" + order.getId() + " : compartiment " + chosen.getNumber() + " du coffre " + chosenBox.getName()
                        + ". Demandez l'ouverture pour charger le repas.", "/restaurant/commandes");
        broadcastBox(chosen.getBoxId());
        return true;
    }

    // =====================================================================
    // Chargement par le restaurant
    // =====================================================================

    /**
     * Delivre au restaurant une autorisation temporaire et ouvre uniquement le compartiment attribue a sa commande.
     * Les echecs materiels sont journalises meme si la requete echoue (pas d'annulation sur ApiException).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public CompartmentView authorizeLoading(Long orderId, AuthUser user) {
        FoodOrder order = orders.lockById(orderId).orElseThrow(() -> ApiException.notFound("Commande introuvable."));
        Restaurant restaurant = restaurants.findById(order.getRestaurantId()).orElse(null);
        if (restaurant == null || !restaurant.getOwnerId().equals(user.id())) {
            throw ApiException.notFound("Commande introuvable.");
        }
        if (order.getStatus() != OrderStatus.RESERVED) {
            throw ApiException.conflict("NOT_RESERVED", "Aucun compartiment n'est reserve pour cette commande.");
        }
        CompartmentAssignment assignment = assignments.findByOrderIdAndActiveTrue(orderId)
                .orElseThrow(() -> ApiException.conflict("NOT_RESERVED", "Aucun compartiment n'est reserve pour cette commande."));
        Compartment compartment = compartments.lockById(assignment.getCompartmentId())
                .orElseThrow(() -> ApiException.notFound("Compartiment introuvable."));
        if (compartment.getState() != CompartmentState.RESERVED) {
            throw ApiException.conflict("ALREADY_OPEN", "Le compartiment est deja ouvert ou en cours de chargement.");
        }

        OpeningAuthorization authorization = new OpeningAuthorization();
        authorization.setCompartmentId(compartment.getId());
        authorization.setOrderId(orderId);
        authorization.setUserId(user.id());
        authorization.setPurpose(AuthPurpose.LOADING);
        authorization.setExpiresAt(Instant.now().plus(props.getLoadingAuthorizationMinutes(), ChronoUnit.MINUTES));
        authorization = authorizations.saveAndFlush(authorization);

        UnlockResult result = gateway.unlock(compartment.getBoxId(), compartment.getNumber());
        if (result != UnlockResult.OK) {
            authorization.setUsedAt(Instant.now());
            authorizations.save(authorization);
            event(compartment.getBoxId(), compartment.getId(), orderId, user.id(), "LOADING_UNLOCK_FAILED", false, result.name());
            reportUnlockFailure(compartment, result);
            throw unlockError(result);
        }
        audit.log(user.id(), "LOADING_AUTHORIZED", "COMPARTMENT", compartment.getId(), "Commande " + orderId);
        broadcastBox(compartment.getBoxId());
        return compartmentView(compartment, orderId, true);
    }

    private static ApiException unlockError(UnlockResult result) {
        return switch (result) {
            case OFFLINE -> ApiException.unavailable("BOX_OFFLINE", "Le coffre est hors connexion. Les compartiments restent verrouilles.");
            case NO_ACK -> ApiException.unavailable("COMMAND_NOT_RECEIVED", "Le coffre n'a pas recu la commande d'ouverture. Reessayez.");
            case LOCK_FAULT -> ApiException.unavailable("LOCK_FAULT", "La serrure ne repond pas. Un technicien a ete prevenu.");
            case OK -> new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Etat inattendu.");
        };
    }

    private void reportUnlockFailure(Compartment compartment, UnlockResult result) {
        if (result == UnlockResult.LOCK_FAULT) {
            notifications.notifyAdmins("LOCK_FAULT", "Serrure defectueuse",
                    "Coffre " + compartment.getBoxId() + ", compartiment " + compartment.getNumber() + " : la serrure n'a pas repondu.", "/admin/coffres");
        } else if (result == UnlockResult.NO_ACK) {
            notifications.notifyAdmins("COMMAND_LOST", "Commande d'ouverture non recue",
                    "Coffre " + compartment.getBoxId() + ", compartiment " + compartment.getNumber() + ".", "/admin/coffres");
        }
    }

    // =====================================================================
    // Evenements du materiel
    // =====================================================================

    @EventListener
    @Transactional
    public void onDoorOpened(DeviceEvents.DoorOpened e) {
        Compartment c = lockCompartment(e.boxId(), e.number());
        if (c == null) {
            return;
        }
        sensor(e.boxId(), e.number(), "DOOR_OPENED", null);
        c.setDoorOpen(true);
        c.setLocked(false);
        Instant now = Instant.now();
        Long orderId = assignments.findByCompartmentIdAndActiveTrue(c.getId()).map(CompartmentAssignment::getOrderId).orElse(null);
        switch (c.getState()) {
            case RESERVED -> {
                OpeningAuthorization authorization = authorizations
                        .findFirstByCompartmentIdAndPurposeAndUsedAtIsNullOrderByIdDesc(c.getId(), AuthPurpose.LOADING).orElse(null);
                if (authorization != null && authorization.getExpiresAt().isAfter(now)) {
                    authorization.setUsedAt(now);
                    authorizations.save(authorization);
                    c.setState(CompartmentState.LOADING);
                    touch(c);
                    event(c.getBoxId(), c.getId(), orderId, authorization.getUserId(), "LOADING_OPENED", true, null);
                } else {
                    abnormalOpening(c, orderId, "Ouverture sans autorisation de chargement valide");
                }
            }
            case WITHDRAWING -> {
                OpeningAuthorization authorization = authorizations
                        .findFirstByCompartmentIdAndPurposeAndUsedAtIsNullOrderByIdDesc(c.getId(), AuthPurpose.WITHDRAWAL).orElse(null);
                if (authorization != null) {
                    authorization.setUsedAt(now);
                    authorizations.save(authorization);
                }
                // Le code est consomme a l'ouverture effective : il ne pourra plus jamais servir.
                codes.findFirstByCompartmentIdAndStatusInOrderByIdDesc(c.getId(), List.of(CodeStatus.ACTIVE)).ifPresent(code -> {
                    code.setStatus(CodeStatus.USED);
                    code.setUsedAt(now);
                    codes.save(code);
                });
                c.setState(CompartmentState.OPEN);
                touch(c);
                event(c.getBoxId(), c.getId(), orderId, authorization == null ? null : authorization.getUserId(), "WITHDRAWAL_OPENED", true, null);
            }
            default -> abnormalOpening(c, orderId, "Ouverture anormale detectee (etat " + c.getState() + ")");
        }
        broadcastBox(c.getBoxId());
    }

    private void abnormalOpening(Compartment c, Long orderId, String reason) {
        fault(c, reason);
        event(c.getBoxId(), c.getId(), orderId, null, "UNAUTHORIZED_OPENING", false, reason);
        audit.security("UNAUTHORIZED_OPENING", null, c.getBoxId(), "Compartiment " + c.getNumber() + " : " + reason);
        notifications.notifyAdmins("UNAUTHORIZED_OPENING", "Ouverture anormale",
                "Coffre " + c.getBoxId() + ", compartiment " + c.getNumber() + " : " + reason + ".", "/admin/coffres");
    }

    @EventListener
    @Transactional
    public void onDoorClosed(DeviceEvents.DoorClosed e) {
        Compartment c = lockCompartment(e.boxId(), e.number());
        if (c == null) {
            return;
        }
        sensor(e.boxId(), e.number(), "DOOR_CLOSED", e.latched() ? "LATCHED" : "UNLATCHED");
        c.setDoorOpen(false);
        c.setLocked(e.latched());
        CompartmentAssignment assignment = assignments.findByCompartmentIdAndActiveTrue(c.getId()).orElse(null);
        FoodOrder order = assignment == null ? null : orders.lockById(assignment.getOrderId()).orElse(null);
        switch (c.getState()) {
            case LOADING -> closeAfterLoading(c, assignment, order, e.latched());
            case OPEN -> closeAfterWithdrawal(c, assignment, order, e.latched());
            default -> touch(c);
        }
        broadcastBox(c.getBoxId());
    }

    private void closeAfterLoading(Compartment c, CompartmentAssignment assignment, FoodOrder order, boolean latched) {
        Long orderId = order == null ? null : order.getId();
        // Etape 1 : la porte est fermee, le repas est a l'interieur.
        c.setState(CompartmentState.OCCUPIED);
        touch(c);
        event(c.getBoxId(), c.getId(), orderId, null, "LOADING_CLOSED", true, null);
        // Etape 2 : verification du verrouillage, par le capteur puis par interrogation du coffre.
        boolean verified = latched && gateway.isLatched(c.getBoxId(), c.getNumber());
        if (!verified || order == null || assignment == null) {
            event(c.getBoxId(), c.getId(), orderId, null, "LOCK_VERIFICATION", false, "Serrure non enclenchee");
            fault(c, "Serrure non verrouillee apres le chargement");
            if (order != null && assignment != null) {
                releaseAfterFailedLoading(c, assignment, order, "La serrure du compartiment " + c.getNumber() + " ne s'est pas verrouillee.");
            }
            notifications.notifyAdmins("LOCK_FAULT", "Serrure defectueuse",
                    "Coffre " + c.getBoxId() + ", compartiment " + c.getNumber() + " : verrouillage non confirme apres chargement.", "/admin/coffres");
            return;
        }
        c.setState(CompartmentState.LOCKED);
        touch(c);
        event(c.getBoxId(), c.getId(), orderId, null, "LOCK_VERIFICATION", true, null);
        // Etape 3 : le code n'est genere qu'apres confirmation du verrouillage.
        WithdrawalCode code = codeService.generate(order, c);
        event(c.getBoxId(), c.getId(), orderId, null, "CODE_GENERATED", true, null);
        orderStatus.transition(order, OrderStatus.LOADED, null, "Repas verrouille dans le compartiment " + c.getNumber());
        notifications.notify(order.getCustomerId(), "CODE_READY", "Votre code de retrait est pret",
                "Votre repas est verrouille dans le compartiment " + c.getNumber()
                        + ". Consultez votre code dans le suivi de la commande (valable jusqu'a "
                        + java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(tg.smartfood.restaurant.RestaurantService.ZONE).format(code.getExpiresAt())
                        + ").", "/commandes/" + order.getGroupId());
        if (order.getDeliveryId() != null) {
            deliveries.findById(order.getDeliveryId()).ifPresent(d -> notifications.notify(d.getCourierId(), "MEAL_LOADED",
                    "Repas charge", "Compartiment " + c.getNumber() + " charge et verrouille.", "/livreur"));
        }
    }

    private void releaseAfterFailedLoading(Compartment c, CompartmentAssignment assignment, FoodOrder order, String reason) {
        assignment.setActive(false);
        assignment.setReleasedAt(Instant.now());
        assignments.saveAndFlush(assignment);
        Long deliveryId = order.getDeliveryId();
        order.setDeliveryId(null);
        orderStatus.transition(order, OrderStatus.READY, null, reason);
        deliveryService.cancelIfEmpty(deliveryId);
        restaurants.findById(order.getRestaurantId()).ifPresent(r -> notifications.notify(r.getOwnerId(), "COMPARTMENT_FAULT",
                "Compartiment en panne", reason + " Reprenez le repas et demandez un autre compartiment pour la commande n°" + order.getId() + ".",
                "/restaurant/commandes"));
    }

    private void closeAfterWithdrawal(Compartment c, CompartmentAssignment assignment, FoodOrder order, boolean latched) {
        Long orderId = order == null ? null : order.getId();
        event(c.getBoxId(), c.getId(), orderId, order == null ? null : order.getCustomerId(), "WITHDRAWAL_CLOSED", true, null);
        if (assignment != null) {
            assignment.setActive(false);
            assignment.setReleasedAt(Instant.now());
            assignments.saveAndFlush(assignment);
        }
        // Le compartiment n'est libere qu'apres verification de son etat.
        if (latched && gateway.isLatched(c.getBoxId(), c.getNumber())) {
            c.setState(CompartmentState.AVAILABLE);
            c.setFaultReason(null);
            touch(c);
            event(c.getBoxId(), c.getId(), orderId, null, "COMPARTMENT_RELEASED", true, null);
        } else {
            fault(c, "Serrure non verrouillee apres le retrait");
            notifications.notifyAdmins("LOCK_FAULT", "Serrure defectueuse",
                    "Coffre " + c.getBoxId() + ", compartiment " + c.getNumber() + " : verrouillage non confirme apres retrait.", "/admin/coffres");
        }
        if (order != null && CARRIED.contains(order.getStatus())) {
            orderStatus.transition(order, OrderStatus.COLLECTED, order.getCustomerId(), "Repas retire du compartiment " + c.getNumber());
            notifications.notify(order.getCustomerId(), "ORDER_COLLECTED", "Bon appetit !",
                    "Le retrait de votre commande n°" + order.getId() + " est enregistre.", "/commandes/" + order.getGroupId());
            notifications.notifyAdmins("ORDER_COLLECTED", "Commande retiree",
                    "Commande n°" + order.getId() + " retiree, compartiment " + c.getNumber() + " du coffre " + c.getBoxId() + ".", "/admin/commandes");
            deliveryService.onOrderClosed(order.getDeliveryId());
        }
    }

    /** Saisie d'un code sur le clavier central. Le serveur retrouve lui-meme le compartiment : le client ne le choisit jamais. */
    @EventListener
    @Transactional
    public void onKeypad(DeviceEvents.KeypadEntered e) {
        DeliveryBox box = boxes.lockById(e.boxId()).orElse(null);
        if (box == null) {
            return;
        }
        Instant now = Instant.now();
        if (box.getKeypadLockedUntil() != null && box.getKeypadLockedUntil().isAfter(now)) {
            event(box.getId(), null, null, null, "CODE_REJECTED", false, "Clavier bloque");
            gateway.display(box.getId(), "CLAVIER BLOQUE");
            return;
        }
        String entered = e.code() == null ? "" : e.code().trim();
        WithdrawalCode code = entered.matches("\\d{6}")
                ? codes.findByBoxIdAndCodeHashAndStatus(box.getId(), codeService.hash(entered), CodeStatus.ACTIVE).orElse(null)
                : null;
        if (code == null) {
            rejectCode(box, now);
            return;
        }
        FoodOrder order = orders.lockById(code.getOrderId()).orElse(null);
        Compartment c = compartments.lockById(code.getCompartmentId()).orElse(null);
        if (order == null || c == null) {
            rejectCode(box, now);
            return;
        }
        if (code.getExpiresAt().isBefore(now)) {
            code.setStatus(CodeStatus.EXPIRED);
            codes.save(code);
            event(box.getId(), c.getId(), order.getId(), order.getCustomerId(), "CODE_EXPIRED", false, null);
            gateway.display(box.getId(), "CODE EXPIRE");
            notifications.notify(order.getCustomerId(), "CODE_EXPIRED", "Code expire",
                    "Votre code de retrait a expire. Demandez un nouveau code depuis le suivi de la commande.", "/commandes/" + order.getGroupId());
            return;
        }
        Payment payment = payments.findByGroupId(order.getGroupId()).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED) {
            event(box.getId(), c.getId(), order.getId(), order.getCustomerId(), "PAYMENT_REQUIRED", false, null);
            gateway.display(box.getId(), "PAIEMENT REQUIS");
            return;
        }
        if (c.getState() != CompartmentState.LOCKED) {
            event(box.getId(), c.getId(), order.getId(), order.getCustomerId(), "CODE_REJECTED", false, "Compartiment " + c.getState());
            gateway.display(box.getId(), "COMPARTIMENT INDISPONIBLE");
            return;
        }
        box.setFailedAttempts(0);
        boxes.save(box);

        OpeningAuthorization authorization = new OpeningAuthorization();
        authorization.setCompartmentId(c.getId());
        authorization.setOrderId(order.getId());
        authorization.setUserId(order.getCustomerId());
        authorization.setPurpose(AuthPurpose.WITHDRAWAL);
        authorization.setExpiresAt(now.plus(2, ChronoUnit.MINUTES));
        authorization = authorizations.saveAndFlush(authorization);

        c.setState(CompartmentState.WITHDRAWING);
        touch(c);
        event(box.getId(), c.getId(), order.getId(), order.getCustomerId(), "CODE_ACCEPTED", true, null);

        UnlockResult result = gateway.unlock(box.getId(), c.getNumber());
        if (result != UnlockResult.OK) {
            // L'ouverture n'a pas eu lieu : le compartiment reste verrouille et le code reste valable.
            authorization.setUsedAt(Instant.now());
            authorizations.save(authorization);
            if (c.getState() == CompartmentState.WITHDRAWING) {
                c.setState(CompartmentState.LOCKED);
                touch(c);
            }
            event(box.getId(), c.getId(), order.getId(), order.getCustomerId(), "WITHDRAWAL_UNLOCK_FAILED", false, result.name());
            reportUnlockFailure(c, result);
            gateway.display(box.getId(), result == UnlockResult.LOCK_FAULT ? "SERRURE EN PANNE" : "ERREUR - REESSAYEZ");
            broadcastBox(box.getId());
            return;
        }
        gateway.display(box.getId(), "COMPARTIMENT " + c.getNumber() + " OUVERT");
        notifications.notify(order.getCustomerId(), "COMPARTMENT_OPEN", "Compartiment " + c.getNumber() + " ouvert",
                "Recuperez votre repas puis refermez la porte.", "/commandes/" + order.getGroupId());
        broadcastBox(box.getId());
    }

    private void rejectCode(DeliveryBox box, Instant now) {
        box.setFailedAttempts(box.getFailedAttempts() + 1);
        event(box.getId(), null, null, null, "CODE_REJECTED", false, "Code incorrect");
        if (box.getFailedAttempts() >= props.getCodeMaxAttempts()) {
            box.setFailedAttempts(0);
            box.setKeypadLockedUntil(now.plus(props.getCodeLockMinutes(), ChronoUnit.MINUTES));
            boxes.save(box);
            audit.security("KEYPAD_LOCKED", null, box.getId(), "Clavier bloque apres " + props.getCodeMaxAttempts() + " codes incorrects");
            notifications.notifyAdmins("KEYPAD_LOCKED", "Tentatives excessives",
                    "Le clavier du coffre " + box.getName() + " est bloque pendant " + props.getCodeLockMinutes() + " minutes.", "/admin/coffres");
            gateway.display(box.getId(), "CLAVIER BLOQUE");
        } else {
            boxes.save(box);
            gateway.display(box.getId(), "CODE INCORRECT");
        }
        broadcastBox(box.getId());
    }

    @EventListener
    @Transactional
    public void onConnectivity(DeviceEvents.Connectivity e) {
        IotDevice device = devices.findByBoxId(e.boxId()).orElse(null);
        DeliveryBox box = boxes.findById(e.boxId()).orElse(null);
        if (device == null || box == null) {
            return;
        }
        device.setOnline(e.online());
        if (e.online()) {
            device.setLastSeenAt(Instant.now());
        }
        devices.save(device);
        sensor(device, null, e.online() ? "CONNECTION_RESTORED" : "CONNECTION_LOST", null);
        if (e.online()) {
            notifications.notifyAdmins("BOX_ONLINE", "Coffre reconnecte", "Le coffre " + box.getName() + " est de nouveau en ligne.", "/admin/coffres");
        } else {
            // Procedure de securite : hors connexion, aucune ouverture n'est possible ; tout reste verrouille.
            String body = "Le coffre " + box.getName() + " est hors connexion. Les compartiments restent verrouilles jusqu'a la reconnexion.";
            notifications.notifyAdmins("BOX_OFFLINE", "Coffre hors connexion", body, "/admin/coffres");
            notifications.notify(box.getCourierId(), "BOX_OFFLINE", "Coffre hors connexion", body, "/livreur");
        }
        broadcastBox(box.getId());
    }

    @EventListener
    @Transactional
    public void onHeartbeat(DeviceEvents.Heartbeat e) {
        IotDevice device = devices.findByBoxId(e.boxId()).orElse(null);
        DeliveryBox box = boxes.findById(e.boxId()).orElse(null);
        if (device == null || box == null) {
            return;
        }
        boolean batteryDropped = e.batteryPct() < LOW_BATTERY && device.getBatteryPct() >= LOW_BATTERY;
        boolean overheated = e.temperatureC() > HIGH_TEMPERATURE && device.getTemperatureC() <= HIGH_TEMPERATURE;
        boolean changed = e.batteryPct() != device.getBatteryPct() || e.temperatureC() != device.getTemperatureC();
        device.setBatteryPct(e.batteryPct());
        device.setTemperatureC(e.temperatureC());
        device.setLastSeenAt(Instant.now());
        devices.save(device);
        DeviceHeartbeat heartbeat = new DeviceHeartbeat();
        heartbeat.setDeviceId(device.getId());
        heartbeat.setBatteryPct(e.batteryPct());
        heartbeat.setTemperatureC(e.temperatureC());
        heartbeats.save(heartbeat);
        if (batteryDropped) {
            sensor(device, null, "BATTERY_LOW", e.batteryPct() + "%");
            String body = "Batterie du coffre " + box.getName() + " a " + e.batteryPct() + " %. Rechargez-le des que possible.";
            notifications.notifyAdmins("BATTERY_LOW", "Batterie faible", body, "/admin/coffres");
            notifications.notify(box.getCourierId(), "BATTERY_LOW", "Batterie faible", body, "/livreur");
        }
        if (overheated) {
            sensor(device, null, "TEMPERATURE_HIGH", e.temperatureC() + "C");
            notifications.notifyAdmins("TEMPERATURE_HIGH", "Temperature elevee",
                    "Le coffre " + box.getName() + " mesure " + e.temperatureC() + " °C.", "/admin/coffres");
        }
        if (changed) {
            broadcastBox(box.getId());
        }
    }

    @EventListener
    @Transactional
    public void onAnomaly(DeviceEvents.Anomaly e) {
        sensor(e.boxId(), e.number(), e.type(), null);
        if (!"DOOR_AJAR".equals(e.type()) || e.number() == null) {
            return;
        }
        Compartment c = compartments.findByBoxIdAndNumber(e.boxId(), e.number()).orElse(null);
        if (c == null) {
            return;
        }
        FoodOrder order = assignments.findByCompartmentIdAndActiveTrue(c.getId())
                .flatMap(a -> orders.findById(a.getOrderId())).orElse(null);
        event(c.getBoxId(), c.getId(), order == null ? null : order.getId(), null, "DOOR_AJAR", false, "Porte mal fermee");
        if (order == null) {
            return;
        }
        String body = "La porte du compartiment " + c.getNumber() + " est mal fermee. Poussez-la jusqu'au clic.";
        if (c.getState() == CompartmentState.LOADING) {
            restaurants.findById(order.getRestaurantId()).ifPresent(r ->
                    notifications.notify(r.getOwnerId(), "DOOR_AJAR", "Porte mal fermee", body, "/restaurant/commandes"));
        } else if (c.getState() == CompartmentState.OPEN) {
            notifications.notify(order.getCustomerId(), "DOOR_AJAR", "Porte mal fermee", body, "/commandes/" + order.getGroupId());
        }
    }

    // =====================================================================
    // Surveillance
    // =====================================================================

    /**
     * Detecte les ouvertures autorisees que le capteur n'a jamais confirmees (capteur defectueux, porte bloquee)
     * et les codes arrives a expiration.
     */
    @Transactional
    public int runWatchdog(Duration grace) {
        Instant limit = Instant.now().minus(grace);
        int incidents = 0;
        for (OpeningAuthorization authorization : authorizations.findByPurposeAndUsedAtIsNullAndCreatedAtBefore(AuthPurpose.LOADING, limit)) {
            authorization.setUsedAt(Instant.now());
            authorizations.save(authorization);
            Compartment c = compartments.lockById(authorization.getCompartmentId()).orElse(null);
            if (c == null || c.getState() != CompartmentState.RESERVED) {
                continue;
            }
            incidents++;
            String reason = "Ouverture autorisee mais jamais detectee par le capteur du compartiment " + c.getNumber() + ".";
            fault(c, "Capteur de porte muet apres une ouverture autorisee");
            event(c.getBoxId(), c.getId(), authorization.getOrderId(), null, "SENSOR_TIMEOUT", false, reason);
            sensor(c.getBoxId(), c.getNumber(), "SENSOR_TIMEOUT", null);
            CompartmentAssignment assignment = assignments.findByCompartmentIdAndActiveTrue(c.getId()).orElse(null);
            FoodOrder order = assignment == null ? null : orders.lockById(assignment.getOrderId()).orElse(null);
            if (order != null && order.getStatus() == OrderStatus.RESERVED) {
                releaseAfterFailedLoading(c, assignment, order, reason);
            }
            notifications.notifyAdmins("SENSOR_FAULT", "Capteur defectueux", "Coffre " + c.getBoxId() + " : " + reason, "/admin/coffres");
            broadcastBox(c.getBoxId());
        }
        for (Compartment c : compartments.findByStateAndUpdatedAtBefore(CompartmentState.WITHDRAWING, limit)) {
            incidents++;
            String reason = "Retrait autorise mais ouverture jamais detectee par le capteur du compartiment " + c.getNumber() + ".";
            fault(c, "Capteur de porte muet pendant un retrait");
            event(c.getBoxId(), c.getId(), null, null, "SENSOR_TIMEOUT", false, reason);
            sensor(c.getBoxId(), c.getNumber(), "SENSOR_TIMEOUT", null);
            notifications.notifyAdmins("SENSOR_FAULT", "Capteur defectueux", "Coffre " + c.getBoxId() + " : " + reason, "/admin/coffres");
            broadcastBox(c.getBoxId());
        }
        for (WithdrawalCode code : codes.findByStatusAndExpiresAtBefore(CodeStatus.ACTIVE, Instant.now())) {
            code.setStatus(CodeStatus.EXPIRED);
            codes.save(code);
            event(code.getBoxId(), code.getCompartmentId(), code.getOrderId(), null, "CODE_EXPIRED", false, "Expiration automatique");
            orders.findById(code.getOrderId()).ifPresent(order -> notifications.notify(order.getCustomerId(), "CODE_EXPIRED", "Code expire",
                    "Votre code de retrait a expire. Demandez un nouveau code depuis le suivi de la commande.", "/commandes/" + order.getGroupId()));
        }
        return incidents;
    }

    // =====================================================================
    // Codes
    // =====================================================================

    /** Le client demande un nouveau code quand le precedent a expire et que son repas est toujours verrouille. */
    @Transactional
    public void renewCode(Long orderId, Long customerId) {
        FoodOrder order = orders.lockById(orderId)
                .filter(o -> o.getCustomerId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Commande introuvable."));
        CompartmentAssignment assignment = assignments.findByOrderIdAndActiveTrue(orderId)
                .orElseThrow(() -> ApiException.conflict("NO_COMPARTMENT", "Cette commande n'est plus dans un compartiment."));
        Compartment c = compartments.lockById(assignment.getCompartmentId())
                .orElseThrow(() -> ApiException.notFound("Compartiment introuvable."));
        if (!CARRIED.contains(order.getStatus()) || c.getState() != CompartmentState.LOCKED) {
            throw ApiException.conflict("NOT_LOCKED", "Un nouveau code ne peut etre emis que pour un repas verrouille.");
        }
        WithdrawalCode active = codes.findByOrderIdAndStatus(orderId, CodeStatus.ACTIVE).orElse(null);
        if (active != null && active.getExpiresAt().isAfter(Instant.now())) {
            throw ApiException.conflict("CODE_STILL_VALID", "Votre code actuel est encore valable.");
        }
        codeService.generate(order, c);
        event(c.getBoxId(), c.getId(), orderId, customerId, "CODE_RENEWED", true, null);
        audit.log(customerId, "CODE_RENEWED", "ORDER", orderId, null);
    }

    // =====================================================================
    // Annulations et remise en service
    // =====================================================================

    /** Annule une livraison avant le depart : libere les compartiments reserves et remet les commandes en attente de compartiment. */
    @Transactional
    public void cancelDelivery(Long deliveryId, Long actorId, String reason) {
        Delivery delivery = deliveries.findById(deliveryId).orElseThrow(() -> ApiException.notFound("Livraison introuvable."));
        if (!DeliveryService.OPEN_FOR_LOADING.contains(delivery.getStatus())) {
            throw ApiException.conflict("BAD_MISSION_STATE", "Seule une livraison non partie peut etre annulee.");
        }
        List<FoodOrder> list = orders.findByDeliveryIdOrderByIdAsc(deliveryId);
        Map<Long, Compartment> compartmentByOrder = new HashMap<>();
        for (FoodOrder order : list) {
            if (order.getStatus() != OrderStatus.RESERVED) {
                throw ApiException.conflict("MEAL_LOADED", "Un repas est deja charge : la livraison ne peut plus etre annulee.");
            }
            CompartmentAssignment assignment = assignments.findByOrderIdAndActiveTrue(order.getId()).orElse(null);
            if (assignment != null) {
                Compartment c = compartments.lockById(assignment.getCompartmentId()).orElse(null);
                if (c != null && c.getState() != CompartmentState.RESERVED) {
                    throw ApiException.conflict("LOADING_IN_PROGRESS", "Un chargement est en cours : fermez d'abord le compartiment.");
                }
                compartmentByOrder.put(order.getId(), c);
            }
        }
        for (FoodOrder order : list) {
            assignments.findByOrderIdAndActiveTrue(order.getId()).ifPresent(a -> {
                a.setActive(false);
                a.setReleasedAt(Instant.now());
                assignments.saveAndFlush(a);
            });
            Compartment c = compartmentByOrder.get(order.getId());
            if (c != null) {
                c.setState(CompartmentState.AVAILABLE);
                touch(c);
                event(c.getBoxId(), c.getId(), order.getId(), actorId, "RESERVATION_RELEASED", true, reason);
            }
            order.setDeliveryId(null);
            orderStatus.transition(order, OrderStatus.READY, actorId, reason);
            restaurants.findById(order.getRestaurantId()).ifPresent(r -> notifications.notify(r.getOwnerId(), "RESERVATION_RELEASED",
                    "Reservation annulee", "Commande n°" + order.getId() + " : " + reason + " Demandez un nouveau compartiment.", "/restaurant/commandes"));
        }
        delivery.setStatus(DeliveryStatus.CANCELLED);
        delivery.setCompletedAt(Instant.now());
        deliveries.save(delivery);
        audit.log(actorId, "DELIVERY_CANCELLED", "DELIVERY", deliveryId, reason);
        broadcastBox(delivery.getBoxId());
    }

    /**
     * Remet en service un compartiment en panne, apres controle : la porte doit etre fermee et la serrure enclenchee.
     * Si une commande y etait encore rattachee, l'administrateur indique si le repas a ete remis au client.
     */
    @Transactional
    public CompartmentView resetCompartment(Long compartmentId, boolean mealDelivered, AuthUser admin) {
        Compartment c = compartments.lockById(compartmentId).orElseThrow(() -> ApiException.notFound("Compartiment introuvable."));
        if (c.getState() != CompartmentState.FAULT) {
            throw ApiException.conflict("NOT_FAULTY", "Seul un compartiment en panne peut etre remis en service.");
        }
        if (!gateway.isLatched(c.getBoxId(), c.getNumber())) {
            throw ApiException.conflict("NOT_LATCHED", "La porte doit etre fermee et la serrure enclenchee avant la remise en service.");
        }
        CompartmentAssignment assignment = assignments.findByCompartmentIdAndActiveTrue(c.getId()).orElse(null);
        if (assignment != null) {
            assignment.setActive(false);
            assignment.setReleasedAt(Instant.now());
            assignments.saveAndFlush(assignment);
            FoodOrder order = orders.lockById(assignment.getOrderId()).orElse(null);
            if (order != null) {
                codes.findByOrderIdAndStatus(order.getId(), CodeStatus.ACTIVE).ifPresent(code -> {
                    code.setStatus(CodeStatus.REVOKED);
                    codes.save(code);
                });
                if (CARRIED.contains(order.getStatus()) || order.getStatus() == OrderStatus.RESERVED) {
                    if (order.getStatus() == OrderStatus.RESERVED) {
                        Long deliveryId = order.getDeliveryId();
                        order.setDeliveryId(null);
                        orderStatus.transition(order, OrderStatus.READY, admin.id(), "Compartiment remis en service");
                        deliveryService.cancelIfEmpty(deliveryId);
                    } else if (mealDelivered) {
                        orderStatus.transition(order, OrderStatus.COLLECTED, admin.id(), "Remise confirmee par un administrateur");
                        deliveryService.onOrderClosed(order.getDeliveryId());
                    } else {
                        orderStatus.transition(order, OrderStatus.CANCELLED, admin.id(), "Annulee apres incident sur le compartiment");
                        paymentService.refund(order.getGroupId(), order.getSubtotal(), admin.id(), "Incident compartiment, commande " + order.getId());
                        notifications.notify(order.getCustomerId(), "ORDER_CANCELLED", "Commande annulee",
                                "Votre commande n°" + order.getId() + " a ete annulee apres un incident technique. Vous serez rembourse si vous avez deja paye.",
                                "/commandes/" + order.getGroupId());
                        deliveryService.onOrderClosed(order.getDeliveryId());
                    }
                }
            }
        }
        c.setState(CompartmentState.AVAILABLE);
        c.setDoorOpen(false);
        c.setLocked(true);
        c.setFaultReason(null);
        touch(c);
        event(c.getBoxId(), c.getId(), null, admin.id(), "COMPARTMENT_RESET", true, null);
        audit.log(admin.id(), "COMPARTMENT_RESET", "COMPARTMENT", c.getId(), null);
        broadcastBox(c.getBoxId());
        return compartmentView(c, null, false);
    }

    // =====================================================================
    // Administration des coffres
    // =====================================================================

    @Transactional
    public CreatedBox createBox(String name, String serial, List<SizeClass> sizes, Long courierId, AuthUser admin) {
        if (boxes.existsBySerial(serial)) {
            throw ApiException.conflict("SERIAL_TAKEN", "Un coffre porte deja ce numero de serie.");
        }
        DeliveryBox box = new DeliveryBox();
        box.setName(name);
        box.setSerial(serial);
        box = boxes.save(box);

        boolean simulated = props.isSimulatorEnabled();
        String secret = Hashing.randomToken(24);
        IotDevice device = new IotDevice();
        device.setBoxId(box.getId());
        device.setDeviceUid("dev-" + serial.toLowerCase());
        device.setSecretHash(passwordEncoder.encode(secret));
        device.setKind(simulated ? DeviceKind.SIMULATED : DeviceKind.ESP32);
        device.setFirmware(simulated ? "sim-1.0" : "esp32-pending");
        device.setOnline(simulated);
        devices.save(device);

        int number = 1;
        for (SizeClass size : sizes) {
            Compartment c = new Compartment();
            c.setBoxId(box.getId());
            c.setNumber(number++);
            c.setSize(size);
            c.setSizeRank(size.rank());
            c = compartments.save(c);
            if (simulated) {
                SimCompartmentHw hw = new SimCompartmentHw();
                hw.setCompartmentId(c.getId());
                simHardware.save(hw);
            }
        }
        audit.log(admin == null ? null : admin.id(), "BOX_CREATED", "BOX", box.getId(), serial + " - " + sizes.size() + " compartiments");
        if (courierId != null) {
            assignCourier(box.getId(), courierId, admin);
        }
        return new CreatedBox(view(boxes.findById(box.getId()).orElseThrow(), null), secret);
    }

    @Transactional
    public BoxView assignCourier(Long boxId, Long courierId, AuthUser admin) {
        DeliveryBox box = boxes.lockById(boxId).orElseThrow(() -> ApiException.notFound("Coffre introuvable."));
        if (box.getCourierId() != null && deliveries.existsByCourierIdAndStatusIn(box.getCourierId(), DeliveryService.ACTIVE)) {
            throw ApiException.conflict("ACTIVE_MISSION", "Le livreur actuel a une mission en cours avec ce coffre.");
        }
        if (courierId != null) {
            User courier = users.findById(courierId).filter(u -> u.getRoles().contains(Role.COURIER))
                    .orElseThrow(() -> ApiException.badRequest("NOT_A_COURIER", "Ce compte n'est pas un livreur."));
            boxes.findByCourierId(courier.getId()).filter(other -> !other.getId().equals(boxId)).ifPresent(other -> {
                throw ApiException.conflict("COURIER_HAS_BOX", "Ce livreur transporte deja le coffre " + other.getName() + ".");
            });
            notifications.notify(courierId, "BOX_ASSIGNED", "Coffre attribue", "Le coffre " + box.getName() + " vous est attribue.", "/livreur");
        }
        box.setCourierId(courierId);
        boxes.save(box);
        audit.log(admin == null ? null : admin.id(), "BOX_COURIER_ASSIGNED", "BOX", boxId, courierId == null ? "aucun" : String.valueOf(courierId));
        broadcastBox(boxId);
        return view(box, null);
    }

    @Transactional
    public BoxView setStatus(Long boxId, BoxStatus status, AuthUser admin) {
        DeliveryBox box = boxes.lockById(boxId).orElseThrow(() -> ApiException.notFound("Coffre introuvable."));
        box.setStatus(status);
        boxes.save(box);
        audit.log(admin.id(), "BOX_STATUS", "BOX", boxId, status.name());
        broadcastBox(boxId);
        return view(box, null);
    }

    @Transactional
    public BoxView unlockKeypad(Long boxId, AuthUser admin) {
        DeliveryBox box = boxes.lockById(boxId).orElseThrow(() -> ApiException.notFound("Coffre introuvable."));
        box.setKeypadLockedUntil(null);
        box.setFailedAttempts(0);
        boxes.save(box);
        audit.log(admin.id(), "KEYPAD_UNLOCKED", "BOX", boxId, null);
        broadcastBox(boxId);
        return view(box, null);
    }

    // =====================================================================
    // Vues
    // =====================================================================

    /** Coffres visibles par l'utilisateur : tous pour un administrateur, le sien pour un livreur, ceux en service pour un restaurant, ceux de ses livraisons pour un client. */
    @Transactional(readOnly = true)
    public List<BoxView> visibleBoxes(AuthUser user) {
        List<DeliveryBox> list;
        if (user.isAdmin()) {
            list = boxes.findAllByOrderByIdAsc();
        } else if (user.has(Role.COURIER)) {
            list = boxes.findByCourierId(user.id()).map(b -> List.of(b)).orElse(List.of());
        } else if (user.has(Role.RESTAURANT)) {
            list = boxes.findByStatusOrderByIdAsc(BoxStatus.ACTIVE);
        } else {
            list = boxes.findAllByOrderByIdAsc().stream().filter(b -> customerHasOrderIn(b.getId(), user.id())).toList();
        }
        return list.stream().map(b -> view(b, user)).toList();
    }

    @Transactional(readOnly = true)
    public BoxView visibleBox(Long boxId, AuthUser user) {
        DeliveryBox box = boxes.findById(boxId).orElseThrow(() -> ApiException.notFound("Coffre introuvable."));
        boolean allowed = user.isAdmin()
                || (user.has(Role.COURIER) && user.id().equals(box.getCourierId()))
                || (user.has(Role.RESTAURANT) && box.getStatus() == BoxStatus.ACTIVE)
                || customerHasOrderIn(boxId, user.id());
        if (!allowed) {
            throw ApiException.notFound("Coffre introuvable.");
        }
        return view(box, user);
    }

    private boolean customerHasOrderIn(Long boxId, Long customerId) {
        List<Compartment> list = compartments.findByBoxIdOrderByNumberAsc(boxId);
        List<Long> orderIds = assignments.findByCompartmentIdInAndActiveTrue(list.stream().map(Compartment::getId).toList())
                .stream().map(CompartmentAssignment::getOrderId).toList();
        return !orderIds.isEmpty() && orders.findByIdIn(orderIds).stream().anyMatch(o -> o.getCustomerId().equals(customerId));
    }

    /** Vue d'un coffre. user == null : vue administrative complete. */
    public BoxView view(DeliveryBox box, AuthUser user) {
        List<Compartment> list = compartments.findByBoxIdOrderByNumberAsc(box.getId());
        Map<Long, Long> orderByCompartment = new HashMap<>();
        for (CompartmentAssignment a : assignments.findByCompartmentIdInAndActiveTrue(list.stream().map(Compartment::getId).toList())) {
            orderByCompartment.put(a.getCompartmentId(), a.getOrderId());
        }
        Map<Long, FoodOrder> orderById = new HashMap<>();
        if (!orderByCompartment.isEmpty()) {
            orders.findByIdIn(orderByCompartment.values()).forEach(o -> orderById.put(o.getId(), o));
        }
        boolean admin = user == null || user.isAdmin();
        Long ownRestaurantId = user != null && user.has(Role.RESTAURANT)
                ? restaurants.findByOwnerId(user.id()).map(Restaurant::getId).orElse(null) : null;
        List<CompartmentView> views = new ArrayList<>();
        for (Compartment c : list) {
            FoodOrder order = orderById.get(orderByCompartment.get(c.getId()));
            boolean mine = order != null && user != null
                    && (order.getCustomerId().equals(user.id()) || (ownRestaurantId != null && ownRestaurantId.equals(order.getRestaurantId())));
            Long visibleOrderId = order != null && (admin || mine) ? order.getId() : null;
            views.add(compartmentView(c, visibleOrderId, mine));
        }
        DeviceView deviceView = devices.findByBoxId(box.getId())
                .map(d -> new DeviceView(d.getDeviceUid(), d.getKind(), d.isOnline(), d.getBatteryPct(), d.getTemperatureC(),
                        d.getDisplayText(), d.getLastSeenAt()))
                .orElse(null);
        String courierName = box.getCourierId() == null ? null : users.findById(box.getCourierId()).map(User::getFullName).orElse(null);
        boolean courierAvailable = box.getCourierId() != null
                && courierProfiles.findById(box.getCourierId()).map(CourierProfile::isAvailable).orElse(false);
        return new BoxView(box.getId(), box.getSerial(), box.getName(), box.getStatus(), box.getCourierId(), courierName,
                courierAvailable, box.getKeypadLockedUntil(), deviceView, views);
    }

    private static CompartmentView compartmentView(Compartment c, Long orderId, boolean mine) {
        return new CompartmentView(c.getId(), c.getNumber(), c.getSize(), c.getState(), c.isDoorOpen(), c.isLocked(),
                c.getFaultReason(), orderId, mine, c.getUpdatedAt());
    }

    // =====================================================================
    // Outils internes
    // =====================================================================

    private Compartment lockCompartment(long boxId, int number) {
        Compartment found = compartments.findByBoxIdAndNumber(boxId, number).orElse(null);
        if (found == null) {
            log.warn("Evenement recu pour un compartiment inconnu : coffre {}, numero {}", boxId, number);
            return null;
        }
        return compartments.lockById(found.getId()).orElse(null);
    }

    private void touch(Compartment c) {
        c.setUpdatedAt(Instant.now());
        compartments.save(c);
    }

    private void fault(Compartment c, String reason) {
        c.setState(CompartmentState.FAULT);
        c.setFaultReason(reason);
        touch(c);
    }

    private void event(Long boxId, Long compartmentId, Long orderId, Long actorId, String type, boolean success, String detail) {
        OpeningEvent entry = new OpeningEvent();
        entry.setBoxId(boxId);
        entry.setCompartmentId(compartmentId);
        entry.setOrderId(orderId);
        entry.setActorId(actorId);
        entry.setType(type);
        entry.setSuccess(success);
        entry.setDetail(detail != null && detail.length() > 300 ? detail.substring(0, 300) : detail);
        openingEvents.save(entry);
    }

    private void sensor(long boxId, Integer number, String type, String value) {
        devices.findByBoxId(boxId).ifPresent(device -> sensor(device, number, type, value));
    }

    private void sensor(IotDevice device, Integer number, String type, String value) {
        SensorEvent entry = new SensorEvent();
        entry.setDeviceId(device.getId());
        entry.setCompartmentNo(number);
        entry.setType(type);
        entry.setValue(value);
        sensorEvents.save(entry);
    }

    /** Previent en temps reel tous ceux qui voient ce coffre : administrateurs, livreur, restaurants et clients concernes. */
    private void broadcastBox(Long boxId) {
        List<Long> recipients = new ArrayList<>(users.findAdminIds());
        boxes.findById(boxId).map(DeliveryBox::getCourierId).ifPresent(recipients::add);
        List<Compartment> list = compartments.findByBoxIdOrderByNumberAsc(boxId);
        List<Long> orderIds = assignments.findByCompartmentIdInAndActiveTrue(list.stream().map(Compartment::getId).toList())
                .stream().map(CompartmentAssignment::getOrderId).toList();
        if (!orderIds.isEmpty()) {
            for (FoodOrder order : orders.findByIdIn(orderIds)) {
                recipients.add(order.getCustomerId());
                restaurants.findById(order.getRestaurantId()).ifPresent(r -> recipients.add(r.getOwnerId()));
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("boxId", boxId);
        realtime.pushAll(recipients, "box.updated", payload);
    }
}
