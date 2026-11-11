package tg.smartfood.delivery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.audit.AuditService;
import tg.smartfood.box.Compartment;
import tg.smartfood.box.CompartmentAssignment;
import tg.smartfood.box.CompartmentAssignmentRepository;
import tg.smartfood.box.CompartmentRepository;
import tg.smartfood.box.DeliveryBox;
import tg.smartfood.box.DeliveryBoxRepository;
import tg.smartfood.common.ApiException;
import tg.smartfood.common.AppProps;
import tg.smartfood.delivery.DeliveryViews.LocationView;
import tg.smartfood.delivery.DeliveryViews.MissionView;
import tg.smartfood.delivery.DeliveryViews.StopView;
import tg.smartfood.delivery.DeliveryViews.TrackingView;
import tg.smartfood.notification.NotificationService;
import tg.smartfood.order.FoodOrder;
import tg.smartfood.order.FoodOrderRepository;
import tg.smartfood.order.OrderGroup;
import tg.smartfood.order.OrderGroupRepository;
import tg.smartfood.order.OrderStatus;
import tg.smartfood.order.OrderStatusService;
import tg.smartfood.payment.Payment;
import tg.smartfood.payment.PaymentMethod;
import tg.smartfood.payment.PaymentService;
import tg.smartfood.payment.PaymentStatus;
import tg.smartfood.realtime.RealtimeService;
import tg.smartfood.restaurant.Restaurant;
import tg.smartfood.restaurant.RestaurantRepository;
import tg.smartfood.user.CourierProfile;
import tg.smartfood.user.CourierProfileRepository;
import tg.smartfood.user.User;
import tg.smartfood.user.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DeliveryService {

    public static final Set<DeliveryStatus> OPEN_FOR_LOADING = EnumSet.of(DeliveryStatus.OFFERED, DeliveryStatus.ACCEPTED);
    public static final Set<DeliveryStatus> ON_THE_ROAD = EnumSet.of(DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.ARRIVED);
    public static final Set<DeliveryStatus> ACTIVE = EnumSet.of(DeliveryStatus.OFFERED, DeliveryStatus.ACCEPTED,
            DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.ARRIVED);
    private static final Set<OrderStatus> CLOSED_ORDER = EnumSet.of(OrderStatus.COLLECTED, OrderStatus.CANCELLED, OrderStatus.REJECTED);

    private final DeliveryRepository deliveries;
    private final DeliveryAssignmentRepository assignments;
    private final DeliveryLocationRepository locations;
    private final FoodOrderRepository orders;
    private final OrderGroupRepository groups;
    private final OrderStatusService orderStatus;
    private final PaymentService payments;
    private final NotificationService notifications;
    private final RealtimeService realtime;
    private final CourierProfileRepository courierProfiles;
    private final UserRepository users;
    private final RestaurantRepository restaurants;
    private final DeliveryBoxRepository boxes;
    private final CompartmentAssignmentRepository compartmentAssignments;
    private final CompartmentRepository compartments;
    private final AuditService audit;
    private final AppProps props;

    public DeliveryService(DeliveryRepository deliveries, DeliveryAssignmentRepository assignments, DeliveryLocationRepository locations,
                           FoodOrderRepository orders, OrderGroupRepository groups, OrderStatusService orderStatus,
                           PaymentService payments, NotificationService notifications, RealtimeService realtime,
                           CourierProfileRepository courierProfiles, UserRepository users, RestaurantRepository restaurants,
                           DeliveryBoxRepository boxes, CompartmentAssignmentRepository compartmentAssignments,
                           CompartmentRepository compartments, AuditService audit, AppProps props) {
        this.deliveries = deliveries;
        this.assignments = assignments;
        this.locations = locations;
        this.orders = orders;
        this.groups = groups;
        this.orderStatus = orderStatus;
        this.payments = payments;
        this.notifications = notifications;
        this.realtime = realtime;
        this.courierProfiles = courierProfiles;
        this.users = users;
        this.restaurants = restaurants;
        this.boxes = boxes;
        this.compartmentAssignments = compartmentAssignments;
        this.compartments = compartments;
        this.audit = audit;
        this.props = props;
    }

    // ---------- Disponibilite ----------

    public CourierProfile profile(Long courierId) {
        return courierProfiles.findById(courierId).orElseGet(() -> {
            CourierProfile created = new CourierProfile();
            created.setUserId(courierId);
            return created;
        });
    }

    @Transactional
    public CourierProfile setAvailability(Long courierId, boolean available) {
        if (!available && deliveries.existsByCourierIdAndStatusIn(courierId, ACTIVE)) {
            throw ApiException.conflict("ACTIVE_MISSION", "Terminez vos missions en cours avant de vous rendre indisponible.");
        }
        CourierProfile profile = profile(courierId);
        profile.setAvailable(available);
        return courierProfiles.save(profile);
    }

    // ---------- Cycle de vie ----------

    /** Cree la livraison d'une commande globale sur un coffre et propose la mission au livreur du coffre. */
    @Transactional
    public Delivery create(OrderGroup group, DeliveryBox box) {
        Delivery delivery = new Delivery();
        delivery.setGroupId(group.getId());
        delivery.setBoxId(box.getId());
        delivery.setCourierId(box.getCourierId());
        delivery.setDestLat(group.getDeliveryLat());
        delivery.setDestLng(group.getDeliveryLng());
        delivery = deliveries.save(delivery);

        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setDeliveryId(delivery.getId());
        assignment.setCourierId(box.getCourierId());
        assignments.save(assignment);

        notifications.notify(box.getCourierId(), "MISSION_OFFERED", "Nouvelle mission",
                "Une livraison vous est proposee pour " + group.getDeliveryAddress() + ".", "/livreur");
        return delivery;
    }

    private Delivery owned(Long deliveryId, Long courierId) {
        return deliveries.findByIdAndCourierId(deliveryId, courierId)
                .orElseThrow(() -> ApiException.notFound("Mission introuvable."));
    }

    private void expect(Delivery delivery, DeliveryStatus expected, String message) {
        if (delivery.getStatus() != expected) {
            throw ApiException.conflict("BAD_MISSION_STATE", message);
        }
    }

    @Transactional
    public Delivery accept(Long deliveryId, Long courierId) {
        Delivery delivery = owned(deliveryId, courierId);
        expect(delivery, DeliveryStatus.OFFERED, "Cette mission n'est plus en attente d'acceptation.");
        delivery.setStatus(DeliveryStatus.ACCEPTED);
        assignments.findFirstByDeliveryIdOrderByIdDesc(deliveryId).ifPresent(a -> {
            a.setStatus(AssignmentStatus.ACCEPTED);
            a.setRespondedAt(Instant.now());
            assignments.save(a);
        });
        Delivery saved = deliveries.save(delivery);
        pushUpdate(saved);
        return saved;
    }

    /** Prise en charge du coffre : tous les repas de la livraison doivent etre charges et verrouilles. */
    @Transactional
    public Delivery pickup(Long deliveryId, Long courierId) {
        Delivery delivery = owned(deliveryId, courierId);
        expect(delivery, DeliveryStatus.ACCEPTED, "Acceptez d'abord la mission.");
        List<FoodOrder> list = orders.findByDeliveryIdOrderByIdAsc(deliveryId);
        if (list.isEmpty() || list.stream().anyMatch(o -> o.getStatus() != OrderStatus.LOADED)) {
            throw ApiException.conflict("NOT_LOADED", "Tous les repas doivent etre charges et verrouilles avant la prise en charge.");
        }
        delivery.setStatus(DeliveryStatus.PICKED_UP);
        delivery.setPickedUpAt(Instant.now());
        Delivery saved = deliveries.save(delivery);
        audit.log(courierId, "DELIVERY_PICKED_UP", "DELIVERY", deliveryId, null);
        pushUpdate(saved);
        return saved;
    }

    @Transactional
    public Delivery depart(Long deliveryId, Long courierId) {
        Delivery delivery = owned(deliveryId, courierId);
        expect(delivery, DeliveryStatus.PICKED_UP, "Confirmez d'abord la prise en charge du coffre.");
        delivery.setStatus(DeliveryStatus.IN_TRANSIT);
        Delivery saved = deliveries.save(delivery);
        for (FoodOrder order : orders.findByDeliveryIdOrderByIdAsc(deliveryId)) {
            if (order.getStatus() == OrderStatus.LOADED) {
                orderStatus.transition(order, OrderStatus.IN_TRANSIT, courierId, "Depart du livreur");
            }
        }
        notifyCustomer(saved, "DELIVERY_STARTED", "Votre commande est en route",
                "Le livreur vient de partir. Suivez-le sur la carte.");
        pushUpdate(saved);
        return saved;
    }

    @Transactional
    public Delivery arrive(Long deliveryId, Long courierId) {
        Delivery delivery = owned(deliveryId, courierId);
        expect(delivery, DeliveryStatus.IN_TRANSIT, "Confirmez d'abord votre depart.");
        delivery.setStatus(DeliveryStatus.ARRIVED);
        delivery.setArrivedAt(Instant.now());
        Delivery saved = deliveries.save(delivery);
        for (FoodOrder order : orders.findByDeliveryIdOrderByIdAsc(deliveryId)) {
            if (order.getStatus() == OrderStatus.IN_TRANSIT) {
                orderStatus.transition(order, OrderStatus.ARRIVED, courierId, "Livreur arrive");
            }
        }
        notifyCustomer(saved, "DELIVERY_ARRIVED", "Votre livreur est arrive",
                "Saisissez votre code de retrait sur le clavier du coffre.");
        pushUpdate(saved);
        return saved;
    }

    /** Paiement a la livraison : le livreur confirme avoir recu les especes. */
    @Transactional
    public Payment cashReceived(Long deliveryId, Long courierId) {
        Delivery delivery = owned(deliveryId, courierId);
        if (!ON_THE_ROAD.contains(delivery.getStatus())) {
            throw ApiException.conflict("BAD_MISSION_STATE", "L'encaissement se confirme pendant la livraison.");
        }
        Payment payment = payments.require(delivery.getGroupId());
        if (payment.getMethod() != PaymentMethod.CASH_ON_DELIVERY) {
            throw ApiException.conflict("NOT_CASH", "Cette commande n'est pas payee a la livraison.");
        }
        return payments.markSucceeded(payment, courierId, "Especes recues par le livreur");
    }

    /** Enregistre une position GPS du livreur et la pousse au client. */
    @Transactional
    public LocationView recordLocation(Long deliveryId, Long courierId, double latitude, double longitude, Double accuracy, boolean simulated) {
        Delivery delivery = owned(deliveryId, courierId);
        if (!ACTIVE.contains(delivery.getStatus()) || delivery.getStatus() == DeliveryStatus.OFFERED) {
            throw ApiException.conflict("BAD_MISSION_STATE", "La position n'est transmise que pendant une mission acceptee.");
        }
        if (simulated && !props.isSimulatorEnabled()) {
            throw ApiException.forbidden("Les positions simulees sont desactivees sur cet environnement.");
        }
        DeliveryLocation location = new DeliveryLocation();
        location.setDeliveryId(deliveryId);
        location.setCourierId(courierId);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAccuracyM(accuracy);
        location.setSimulated(simulated);
        locations.save(location);
        LocationView view = locationView(location);
        groups.findById(delivery.getGroupId()).ifPresent(group -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("deliveryId", deliveryId);
            payload.put("groupId", group.getId());
            payload.put("location", view);
            realtime.push(group.getCustomerId(), "delivery.location", payload);
        });
        return view;
    }

    /** Appele quand une sous-commande est retiree, annulee ou refusee : cloture la livraison si plus rien n'est a livrer. */
    @Transactional
    public void onOrderClosed(Long deliveryId) {
        if (deliveryId == null) {
            return;
        }
        Delivery delivery = deliveries.findById(deliveryId).orElse(null);
        if (delivery == null || !ACTIVE.contains(delivery.getStatus())) {
            return;
        }
        List<FoodOrder> list = orders.findByDeliveryIdOrderByIdAsc(deliveryId);
        if (list.stream().anyMatch(o -> !CLOSED_ORDER.contains(o.getStatus()))) {
            return;
        }
        boolean delivered = list.stream().anyMatch(o -> o.getStatus() == OrderStatus.COLLECTED);
        delivery.setStatus(delivered ? DeliveryStatus.COMPLETED : DeliveryStatus.CANCELLED);
        delivery.setCompletedAt(Instant.now());
        if (delivered) {
            delivery.setCourierEarning(estimateEarning(delivery.getGroupId()));
        }
        Delivery saved = deliveries.save(delivery);
        notifications.notify(saved.getCourierId(), delivered ? "MISSION_COMPLETED" : "MISSION_CANCELLED",
                delivered ? "Mission terminee" : "Mission annulee",
                delivered ? "Tous les repas ont ete retires. Gain : " + saved.getCourierEarning() + " FCFA." : "La mission a ete annulee.",
                "/livreur");
        pushUpdate(saved);
    }

    /** Annule une livraison qui n'a plus aucune sous-commande rattachee. */
    @Transactional
    public void cancelIfEmpty(Long deliveryId) {
        if (deliveryId == null) {
            return;
        }
        deliveries.findById(deliveryId).ifPresent(delivery -> {
            if (OPEN_FOR_LOADING.contains(delivery.getStatus()) && orders.findByDeliveryIdOrderByIdAsc(deliveryId).isEmpty()) {
                delivery.setStatus(DeliveryStatus.CANCELLED);
                delivery.setCompletedAt(Instant.now());
                deliveries.save(delivery);
                notifications.notify(delivery.getCourierId(), "MISSION_CANCELLED", "Mission annulee",
                        "La mission a ete annulee.", "/livreur");
                pushUpdate(delivery);
            }
        });
    }

    private int estimateEarning(Long groupId) {
        return groups.findById(groupId)
                .map(g -> Math.round(g.getDeliveryFee() * props.getCourierSharePercent() / 100f))
                .orElse(0);
    }

    private void notifyCustomer(Delivery delivery, String type, String title, String body) {
        groups.findById(delivery.getGroupId()).ifPresent(group ->
                notifications.notify(group.getCustomerId(), type, title, body, "/commandes/" + group.getId()));
    }

    private void pushUpdate(Delivery delivery) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deliveryId", delivery.getId());
        payload.put("groupId", delivery.getGroupId());
        payload.put("status", delivery.getStatus());
        List<Long> recipients = new ArrayList<>();
        recipients.add(delivery.getCourierId());
        groups.findById(delivery.getGroupId()).ifPresent(g -> recipients.add(g.getCustomerId()));
        recipients.addAll(users.findAdminIds());
        realtime.pushAll(recipients, "delivery.updated", payload);
    }

    // ---------- Vues ----------

    private static LocationView locationView(DeliveryLocation l) {
        return new LocationView(l.getLatitude(), l.getLongitude(), l.getAccuracyM(), l.isSimulated(), l.getRecordedAt());
    }

    private List<StopView> stops(Long deliveryId) {
        List<FoodOrder> list = orders.findByDeliveryIdOrderByIdAsc(deliveryId);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> numbers = new LinkedHashMap<>();
        for (CompartmentAssignment a : compartmentAssignments.findByOrderIdInAndActiveTrue(list.stream().map(FoodOrder::getId).toList())) {
            compartments.findById(a.getCompartmentId()).map(Compartment::getNumber).ifPresent(n -> numbers.put(a.getOrderId(), n));
        }
        List<StopView> result = new ArrayList<>();
        for (FoodOrder order : list) {
            Restaurant r = restaurants.findById(order.getRestaurantId()).orElse(null);
            if (r != null) {
                result.add(new StopView(order.getId(), order.getStatus(), r.getName(), r.getAddressLine() + ", " + r.getCity(),
                        r.getLatitude(), r.getLongitude(), numbers.get(order.getId())));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<MissionView> missions(Long courierId) {
        return deliveries.findByCourierIdOrderByIdDesc(courierId).stream().limit(100).map(this::mission).toList();
    }

    @Transactional(readOnly = true)
    public MissionView mission(Long deliveryId, Long courierId) {
        return mission(owned(deliveryId, courierId));
    }

    private MissionView mission(Delivery d) {
        OrderGroup group = groups.findById(d.getGroupId()).orElseThrow(() -> ApiException.notFound("Commande introuvable."));
        User customer = users.findById(group.getCustomerId()).orElseThrow(() -> ApiException.notFound("Client introuvable."));
        Payment payment = payments.require(group.getId());
        boolean cashDue = payment.getMethod() == PaymentMethod.CASH_ON_DELIVERY && payment.getStatus() == PaymentStatus.PENDING;
        int earning = d.getStatus() == DeliveryStatus.COMPLETED ? d.getCourierEarning() : estimateEarning(group.getId());
        String boxName = boxes.findById(d.getBoxId()).map(DeliveryBox::getName).orElse("");
        return new MissionView(d.getId(), d.getStatus(), d.getBoxId(), boxName, group.getDeliveryAddress(), d.getDestLat(), d.getDestLng(),
                customer.getFullName(), customer.getPhone(), payment.getMethod(), payment.getStatus(),
                cashDue ? payment.getAmount() : 0, earning, d.getCreatedAt(), d.getCompletedAt(), stops(d.getId()));
    }

    /** Suivi des livraisons d'une commande globale, pour son client uniquement. */
    @Transactional(readOnly = true)
    public List<TrackingView> tracking(Long groupId, Long customerId) {
        OrderGroup group = groups.findById(groupId)
                .filter(g -> g.getCustomerId().equals(customerId))
                .orElseThrow(() -> ApiException.notFound("Commande introuvable."));
        List<TrackingView> result = new ArrayList<>();
        for (Delivery d : deliveries.findByGroupIdOrderByIdAsc(group.getId())) {
            if (d.getStatus() == DeliveryStatus.CANCELLED) {
                continue;
            }
            User courier = users.findById(d.getCourierId()).orElse(null);
            List<LocationView> path = new ArrayList<>();
            // Le trajet n'est montre au client qu'a partir de la prise en charge du coffre.
            if (ON_THE_ROAD.contains(d.getStatus())) {
                locations.findTop300ByDeliveryIdOrderByIdDesc(d.getId()).forEach(l -> path.add(locationView(l)));
                Collections.reverse(path);
            }
            LocationView last = path.isEmpty() ? null : path.get(path.size() - 1);
            String boxName = boxes.findById(d.getBoxId()).map(DeliveryBox::getName).orElse("");
            result.add(new TrackingView(d.getId(), d.getStatus(), courier == null ? "" : courier.getFullName(),
                    courier == null ? "" : courier.getPhone(), boxName, d.getDestLat(), d.getDestLng(), stops(d.getId()), last, path));
        }
        return result;
    }
}
