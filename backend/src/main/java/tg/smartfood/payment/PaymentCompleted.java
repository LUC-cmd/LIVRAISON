package tg.smartfood.payment;

/** Evenement applicatif : le paiement d'une commande globale a abouti (success) ou echoue. */
public record PaymentCompleted(Long groupId, boolean success) {
}
