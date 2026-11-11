package tg.smartfood.courier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tg.smartfood.box.BoxService;
import tg.smartfood.box.BoxViews.BoxView;
import tg.smartfood.common.AuthUser;
import tg.smartfood.delivery.DeliveryRepository;
import tg.smartfood.delivery.DeliveryService;
import tg.smartfood.delivery.DeliveryStatus;
import tg.smartfood.delivery.DeliveryViews.LocationView;
import tg.smartfood.delivery.DeliveryViews.MissionView;
import tg.smartfood.restaurant.RestaurantService;
import tg.smartfood.user.CourierProfile;

import java.time.LocalDate;
import java.util.List;

/**
 * Espace livreur : disponibilite, missions, position GPS, revenus.
 * Aucune route de cet espace ne renvoie un code de retrait ni ne permet d'ouvrir un compartiment.
 */
@RestController
@RequestMapping("/api/v1/courier")
public class CourierController {

    public record AvailabilityRequest(boolean available) {
    }

    public record LocationRequest(
            @NotNull(message = "Latitude manquante.") @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull(message = "Longitude manquante.") @DecimalMin("-180") @DecimalMax("180") Double longitude,
            Double accuracyM, boolean simulated) {
    }

    public record CourierState(boolean available, String vehicle, BoxView box) {
    }

    public record Earnings(int completedDeliveries, int total, int today) {
    }

    private final DeliveryService deliveryService;
    private final DeliveryRepository deliveries;
    private final BoxService boxService;

    public CourierController(DeliveryService deliveryService, DeliveryRepository deliveries, BoxService boxService) {
        this.deliveryService = deliveryService;
        this.deliveries = deliveries;
        this.boxService = boxService;
    }

    @GetMapping("/state")
    public CourierState state(@AuthenticationPrincipal AuthUser user) {
        CourierProfile profile = deliveryService.profile(user.id());
        List<BoxView> boxes = boxService.visibleBoxes(user);
        return new CourierState(profile.isAvailable(), profile.getVehicle(), boxes.isEmpty() ? null : boxes.get(0));
    }

    @PutMapping("/availability")
    public CourierState availability(@AuthenticationPrincipal AuthUser user, @RequestBody AvailabilityRequest request) {
        deliveryService.setAvailability(user.id(), request.available());
        return state(user);
    }

    @GetMapping("/missions")
    public List<MissionView> missions(@AuthenticationPrincipal AuthUser user) {
        return deliveryService.missions(user.id());
    }

    @PostMapping("/missions/{id}/accept")
    public MissionView accept(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        deliveryService.accept(id, user.id());
        return deliveryService.mission(id, user.id());
    }

    /** Refus d'une mission avant tout chargement : les compartiments reserves sont liberes. */
    @PostMapping("/missions/{id}/reject")
    public List<MissionView> reject(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        deliveryService.mission(id, user.id());
        boxService.cancelDelivery(id, user.id(), "Mission refusee par le livreur.");
        return deliveryService.missions(user.id());
    }

    @PostMapping("/missions/{id}/pickup")
    public MissionView pickup(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        deliveryService.pickup(id, user.id());
        return deliveryService.mission(id, user.id());
    }

    @PostMapping("/missions/{id}/depart")
    public MissionView depart(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        deliveryService.depart(id, user.id());
        return deliveryService.mission(id, user.id());
    }

    @PostMapping("/missions/{id}/arrive")
    public MissionView arrive(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        deliveryService.arrive(id, user.id());
        return deliveryService.mission(id, user.id());
    }

    @PostMapping("/missions/{id}/cash-received")
    public MissionView cashReceived(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        deliveryService.cashReceived(id, user.id());
        return deliveryService.mission(id, user.id());
    }

    @PostMapping("/missions/{id}/location")
    public LocationView location(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody LocationRequest request) {
        return deliveryService.recordLocation(id, user.id(), request.latitude(), request.longitude(), request.accuracyM(), request.simulated());
    }

    @GetMapping("/earnings")
    public Earnings earnings(@AuthenticationPrincipal AuthUser user) {
        LocalDate today = LocalDate.now(RestaurantService.ZONE);
        int count = 0;
        int total = 0;
        int todayTotal = 0;
        for (var d : deliveries.findByCourierIdOrderByIdDesc(user.id())) {
            if (d.getStatus() != DeliveryStatus.COMPLETED) {
                continue;
            }
            count++;
            total += d.getCourierEarning();
            if (d.getCompletedAt() != null && d.getCompletedAt().atZone(RestaurantService.ZONE).toLocalDate().equals(today)) {
                todayTotal += d.getCourierEarning();
            }
        }
        return new Earnings(count, total, todayTotal);
    }
}
