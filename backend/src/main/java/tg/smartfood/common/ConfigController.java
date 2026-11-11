package tg.smartfood.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.smartfood.payment.PaymentService;

import java.util.LinkedHashMap;
import java.util.Map;

/** Parametres publics dont l'interface a besoin : moyens de paiement actifs, mode simulation, frais. */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final AppProps props;
    private final PaymentService payments;

    public ConfigController(AppProps props, PaymentService payments) {
        this.props = props;
        this.payments = payments;
    }

    @GetMapping
    public Map<String, Object> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("paymentMethods", payments.availableMethods());
        config.put("paymentsSandbox", props.isPaymentsSandboxEnabled());
        config.put("simulatorEnabled", props.isSimulatorEnabled());
        config.put("deliveryFee", props.getDeliveryFee());
        config.put("commissionPercent", props.getCommissionPercent());
        config.put("courierSharePercent", props.getCourierSharePercent());
        config.put("codeValidityMinutes", props.getCodeValidityMinutes());
        return config;
    }
}
