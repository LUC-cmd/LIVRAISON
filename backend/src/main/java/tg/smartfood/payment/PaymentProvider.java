package tg.smartfood.payment;

import java.util.Map;

/**
 * Contrat d'un prestataire de paiement. Une integration reelle (Mixx by Yas, Flooz, carte bancaire)
 * implemente cette interface avec l'API officielle du prestataire ; aucun autre module ne change.
 */
public interface PaymentProvider {

    /** Identifiant stable du prestataire, utilise dans payments.provider et dans l'URL de webhook. */
    String name();

    boolean supports(PaymentMethod method);

    /** Demarre l'encaissement et retourne la reference du prestataire (peut etre nulle). Le paiement reste EN ATTENTE. */
    String initiate(Payment payment);

    /** Rembourse le montant indique. Retourne la reference du remboursement. */
    String refund(Payment payment, int amount);

    /**
     * Interprete une notification du prestataire apres avoir verifie sa signature.
     * Retourne null si le prestataire n'utilise pas de webhooks ou si l'evenement ne concerne pas un paiement.
     */
    default WebhookOutcome handleWebhook(String payload, Map<String, String> headers) {
        return null;
    }

    record WebhookOutcome(String providerRef, boolean success, String detail) {
    }
}
