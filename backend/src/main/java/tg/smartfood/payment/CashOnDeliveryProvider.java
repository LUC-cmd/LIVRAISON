package tg.smartfood.payment;

import org.springframework.stereotype.Component;

/** Paiement a la livraison : l'encaissement est confirme par le livreur a l'arrivee. */
@Component
public class CashOnDeliveryProvider implements PaymentProvider {

    @Override
    public String name() {
        return "cash";
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CASH_ON_DELIVERY;
    }

    @Override
    public String initiate(Payment payment) {
        return "CASH-" + payment.getGroupId();
    }

    @Override
    public String refund(Payment payment, int amount) {
        // Especes : le remboursement est remis en main propre ; la transaction en garde la trace.
        return "CASH-REFUND-" + payment.getGroupId();
    }
}
