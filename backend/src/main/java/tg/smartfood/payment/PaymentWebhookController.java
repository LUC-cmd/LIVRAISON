package tg.smartfood.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tg.smartfood.common.ApiException;

import java.util.Map;

/** Point d'entree des notifications des prestataires de paiement. La signature est verifiee par le prestataire concerne. */
@RestController
@RequestMapping("/api/v1/payments/webhooks")
public class PaymentWebhookController {

    private final PaymentService payments;

    public PaymentWebhookController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(@PathVariable String provider, @RequestHeader(value = "X-Event-Id", required = false) String eventId,
                        @RequestHeader Map<String, String> headers, @RequestBody String payload) {
        if (eventId == null || eventId.isBlank() || eventId.length() > 120) {
            throw ApiException.badRequest("MISSING_EVENT_ID", "L'en-tete X-Event-Id est obligatoire.");
        }
        payments.receiveWebhook(provider, eventId, payload, headers);
    }
}
