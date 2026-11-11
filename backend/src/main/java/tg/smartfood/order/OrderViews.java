package tg.smartfood.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tg.smartfood.box.CompartmentState;
import tg.smartfood.payment.PaymentMethod;
import tg.smartfood.payment.PaymentStatus;
import tg.smartfood.restaurant.SizeClass;

import java.time.Instant;
import java.util.List;

public final class OrderViews {

    private OrderViews() {
    }

    public record Line(@NotNull(message = "Plat manquant.") Long dishId,
                       @Min(value = 1, message = "Quantite minimale : 1.") @Max(value = 50, message = "Quantite maximale : 50.") int quantity) {
    }

    public record CheckoutRequest(
            @NotNull(message = "Choisissez une adresse de livraison.") Long addressId,
            @NotNull(message = "Choisissez un moyen de paiement.") PaymentMethod paymentMethod,
            @NotEmpty(message = "Votre panier est vide.") @Size(max = 30, message = "Le panier contient trop de lignes.") List<@Valid Line> items) {
    }

    public record ItemView(Long dishId, String name, int unitPrice, int quantity) {
    }

    public record HistoryView(OrderStatus status, String note, Instant at) {
    }

    /** Sous-commande d'un restaurant. Le code de retrait n'y figure jamais : il se demande par un appel reserve au client. */
    public record OrderView(Long id, Long groupId, Long restaurantId, String restaurantName, OrderStatus status, int subtotal,
                            SizeClass requiredSize, String rejectReason, Instant createdAt, Instant updatedAt,
                            List<ItemView> items, List<HistoryView> history, Long boxId, String boxName, Integer compartmentNumber,
                            CompartmentState compartmentState, Long deliveryId, boolean codeActive, Instant codeExpiresAt) {
    }

    public record PaymentView(PaymentMethod method, PaymentStatus status, int amount, String provider) {
    }

    /** Commande globale : une ou plusieurs sous-commandes independantes. */
    public record GroupView(Long id, Long customerId, String customerName, Instant createdAt, String deliveryAddress, double deliveryLat,
                            double deliveryLng, int subtotal, int deliveryFee, int total, PaymentView payment, List<OrderView> orders) {
    }

    public record RestaurantOrderView(OrderView order, String customerName, int commission, int net) {
    }
}
