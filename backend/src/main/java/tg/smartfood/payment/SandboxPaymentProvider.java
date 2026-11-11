package tg.smartfood.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tg.smartfood.common.Hashing;

/**
 * Prestataire de test pour Mixx by Yas, Flooz et carte bancaire. Aucun argent ne circule :
 * le client valide ou refuse lui-meme le paiement depuis l'ecran de test.
 * Actif uniquement si smartfood.payments.sandbox-enabled=true ; desactive par le profil de production.
 */
@Component
@ConditionalOnProperty(name = "smartfood.payments.sandbox-enabled", havingValue = "true")
public class SandboxPaymentProvider implements PaymentProvider {

    public static final String NAME = "sandbox";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.MIXX || method == PaymentMethod.FLOOZ || method == PaymentMethod.CARD;
    }

    @Override
    public String initiate(Payment payment) {
        return "SBX-" + Hashing.randomToken(9);
    }

    @Override
    public String refund(Payment payment, int amount) {
        return "SBX-RF-" + Hashing.randomToken(9);
    }
}
