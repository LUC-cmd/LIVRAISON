package tg.smartfood.delivery;

import tg.smartfood.order.OrderStatus;
import tg.smartfood.payment.PaymentMethod;
import tg.smartfood.payment.PaymentStatus;

import java.time.Instant;
import java.util.List;

public final class DeliveryViews {

    private DeliveryViews() {
    }

    /** Point de collecte : un restaurant et le compartiment attribue. */
    public record StopView(Long orderId, OrderStatus orderStatus, String restaurantName, String restaurantAddress,
                           double latitude, double longitude, Integer compartmentNumber) {
    }

    public record LocationView(double latitude, double longitude, Double accuracyM, boolean simulated, Instant recordedAt) {
    }

    /** Mission vue par le livreur. Ne contient jamais de code de retrait. */
    public record MissionView(Long id, DeliveryStatus status, Long boxId, String boxName, String destinationAddress,
                              double destLat, double destLng, String customerName, String customerPhone,
                              PaymentMethod paymentMethod, PaymentStatus paymentStatus, int amountToCollect, int earning,
                              Instant createdAt, Instant completedAt, List<StopView> stops) {
    }

    /** Suivi vu par le client. */
    public record TrackingView(Long deliveryId, DeliveryStatus status, String courierName, String courierPhone, String boxName,
                               double destLat, double destLng, List<StopView> pickups, LocationView lastLocation,
                               List<LocationView> path) {
    }
}
