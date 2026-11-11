package tg.smartfood.payment;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.audit.AuditService;
import tg.smartfood.common.ApiException;
import tg.smartfood.payment.PaymentProvider.WebhookOutcome;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final PaymentWebhookRepository webhooks;
    private final List<PaymentProvider> providers;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    public PaymentService(PaymentRepository payments, PaymentTransactionRepository transactions, PaymentWebhookRepository webhooks,
                          List<PaymentProvider> providers, ApplicationEventPublisher events, AuditService audit) {
        this.payments = payments;
        this.transactions = transactions;
        this.webhooks = webhooks;
        this.providers = providers;
        this.events = events;
        this.audit = audit;
    }

    public List<PaymentMethod> availableMethods() {
        return java.util.Arrays.stream(PaymentMethod.values())
                .filter(m -> providers.stream().anyMatch(p -> p.supports(m)))
                .toList();
    }

    private PaymentProvider providerFor(PaymentMethod method) {
        return providers.stream().filter(p -> p.supports(method)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("PAYMENT_METHOD_UNAVAILABLE", "Ce moyen de paiement n'est pas encore disponible."));
    }

    private PaymentProvider providerNamed(String name) {
        return providers.stream().filter(p -> p.name().equals(name)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Prestataire de paiement inconnu."));
    }

    public Payment require(Long groupId) {
        return payments.findByGroupId(groupId).orElseThrow(() -> ApiException.notFound("Paiement introuvable."));
    }

    @Transactional
    public Payment create(Long groupId, PaymentMethod method, int amount) {
        PaymentProvider provider = providerFor(method);
        Payment payment = new Payment();
        payment.setGroupId(groupId);
        payment.setMethod(method);
        payment.setAmount(amount);
        payment.setProvider(provider.name());
        payment = payments.save(payment);
        payment.setProviderRef(provider.initiate(payment));
        payment = payments.save(payment);
        transaction(payment, TxType.CHARGE, TxStatus.PENDING, amount, payment.getProviderRef(), "Paiement initie");
        return payment;
    }

    @Transactional
    public Payment markSucceeded(Payment payment, Long actorId, String detail) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return payment;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw ApiException.conflict("PAYMENT_CLOSED", "Ce paiement est deja cloture.");
        }
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setUpdatedAt(Instant.now());
        Payment saved = payments.save(payment);
        transaction(saved, TxType.CHARGE, TxStatus.SUCCEEDED, saved.getAmount(), saved.getProviderRef(), detail);
        audit.log(actorId, "PAYMENT_SUCCEEDED", "PAYMENT", saved.getId(), detail);
        events.publishEvent(new PaymentCompleted(saved.getGroupId(), true));
        return saved;
    }

    @Transactional
    public Payment markFailed(Payment payment, Long actorId, String detail) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return payment;
        }
        payment.setStatus(PaymentStatus.FAILED);
        payment.setUpdatedAt(Instant.now());
        Payment saved = payments.save(payment);
        transaction(saved, TxType.CHARGE, TxStatus.FAILED, saved.getAmount(), saved.getProviderRef(), detail);
        audit.log(actorId, "PAYMENT_FAILED", "PAYMENT", saved.getId(), detail);
        events.publishEvent(new PaymentCompleted(saved.getGroupId(), false));
        return saved;
    }

    /**
     * Rembourse une partie ou la totalite d'un paiement encaisse. Sans effet si rien n'a ete encaisse.
     * Le paiement passe a REMBOURSE quand le cumul des remboursements atteint son montant.
     */
    @Transactional
    public void refund(Long groupId, int amount, Long actorId, String reason) {
        Payment payment = require(groupId);
        if (payment.getStatus() != PaymentStatus.SUCCEEDED || amount <= 0) {
            return;
        }
        int alreadyRefunded = transactions.findByPaymentIdOrderByIdAsc(payment.getId()).stream()
                .filter(t -> t.getType() == TxType.REFUND && t.getStatus() == TxStatus.SUCCEEDED)
                .mapToInt(PaymentTransaction::getAmount).sum();
        int refundable = Math.min(amount, payment.getAmount() - alreadyRefunded);
        if (refundable <= 0) {
            return;
        }
        String reference = providerNamed(payment.getProvider()).refund(payment, refundable);
        transaction(payment, TxType.REFUND, TxStatus.SUCCEEDED, refundable, reference, reason);
        if (alreadyRefunded + refundable >= payment.getAmount()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setUpdatedAt(Instant.now());
            payments.save(payment);
        }
        audit.log(actorId, "PAYMENT_REFUND", "PAYMENT", payment.getId(), refundable + " FCFA - " + reason);
    }

    /** Annule un paiement encore en attente (commande entierement annulee ou refusee). */
    @Transactional
    public void cancelPending(Long groupId, Long actorId, String reason) {
        Payment payment = require(groupId);
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setUpdatedAt(Instant.now());
            payments.save(payment);
            transaction(payment, TxType.CHARGE, TxStatus.FAILED, payment.getAmount(), payment.getProviderRef(), reason);
            audit.log(actorId, "PAYMENT_CANCELLED", "PAYMENT", payment.getId(), reason);
        }
    }

    /** Relance un paiement echoue, avec le meme moyen de paiement ou un autre. Le paiement repasse EN ATTENTE. */
    @Transactional
    public Payment retry(Long groupId, PaymentMethod method, Long actorId) {
        Payment payment = require(groupId);
        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw ApiException.conflict("PAYMENT_NOT_FAILED", "Seul un paiement echoue peut etre relance.");
        }
        PaymentProvider provider = providerFor(method);
        payment.setMethod(method);
        payment.setProvider(provider.name());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setUpdatedAt(Instant.now());
        payment.setProviderRef(provider.initiate(payment));
        Payment saved = payments.save(payment);
        transaction(saved, TxType.CHARGE, TxStatus.PENDING, saved.getAmount(), saved.getProviderRef(), "Nouvelle tentative de paiement");
        audit.log(actorId, "PAYMENT_RETRY", "PAYMENT", saved.getId(), method.name());
        return saved;
    }

    /** Diminue le montant d'un paiement encore en attente quand une sous-commande est refusee ou annulee. */
    @Transactional
    public void reducePending(Long groupId, int reduction, Long actorId, String reason) {
        Payment payment = require(groupId);
        if (payment.getStatus() != PaymentStatus.PENDING || reduction <= 0 || reduction >= payment.getAmount()) {
            return;
        }
        payment.setAmount(payment.getAmount() - reduction);
        payment.setUpdatedAt(Instant.now());
        payments.save(payment);
        audit.log(actorId, "PAYMENT_REDUCED", "PAYMENT", payment.getId(), "-" + reduction + " FCFA - " + reason);
    }

    /** Recoit une notification d'un prestataire. Un evenement deja recu est ignore (idempotence). */
    @Transactional
    public void receiveWebhook(String providerName, String eventId, String payload, Map<String, String> headers) {
        PaymentProvider provider = providerNamed(providerName);
        if (webhooks.existsByProviderAndEventId(providerName, eventId)) {
            return;
        }
        PaymentWebhook webhook = new PaymentWebhook();
        webhook.setProvider(providerName);
        webhook.setEventId(eventId);
        webhook.setPayload(payload);
        WebhookOutcome outcome = provider.handleWebhook(payload, headers);
        if (outcome != null) {
            Payment payment = payments.findByProviderAndProviderRef(providerName, outcome.providerRef())
                    .orElseThrow(() -> ApiException.notFound("Paiement introuvable pour cette notification."));
            if (outcome.success()) {
                markSucceeded(payment, null, outcome.detail());
            } else {
                markFailed(payment, null, outcome.detail());
            }
            webhook.setProcessedAt(Instant.now());
        }
        webhooks.save(webhook);
    }

    private void transaction(Payment payment, TxType type, TxStatus status, int amount, String reference, String detail) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setPaymentId(payment.getId());
        tx.setType(type);
        tx.setStatus(status);
        tx.setAmount(amount);
        tx.setProviderRef(reference);
        tx.setDetail(detail != null && detail.length() > 300 ? detail.substring(0, 300) : detail);
        transactions.save(tx);
    }
}
