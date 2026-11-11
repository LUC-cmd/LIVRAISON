package tg.smartfood.common;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Parametres de la plateforme, lus dans application.yml ou les variables d'environnement. */
@Component
@Getter
public class AppProps {

    @Value("${smartfood.jwt.secret}")
    private String jwtSecret;

    @Value("${smartfood.jwt.access-minutes}")
    private long accessMinutes;

    @Value("${smartfood.jwt.refresh-days}")
    private long refreshDays;

    @Value("${smartfood.codes.hmac-secret}")
    private String codeHmacSecret;

    @Value("${smartfood.codes.aes-key}")
    private String codeAesKey;

    @Value("${smartfood.codes.validity-minutes}")
    private long codeValidityMinutes;

    @Value("${smartfood.codes.max-attempts}")
    private int codeMaxAttempts;

    @Value("${smartfood.codes.lock-minutes}")
    private long codeLockMinutes;

    @Value("${smartfood.loading-authorization-minutes}")
    private long loadingAuthorizationMinutes;

    @Value("${smartfood.pricing.delivery-fee}")
    private int deliveryFee;

    @Value("${smartfood.pricing.commission-percent}")
    private int commissionPercent;

    @Value("${smartfood.pricing.courier-share-percent}")
    private int courierSharePercent;

    @Value("${smartfood.simulator.enabled}")
    private boolean simulatorEnabled;

    @Value("${smartfood.payments.sandbox-enabled}")
    private boolean paymentsSandboxEnabled;

    @Value("${smartfood.storage.dir}")
    private String storageDir;

    @Value("${smartfood.cors.allowed-origins}")
    private String corsAllowedOrigins;

    @Value("${smartfood.mail.from}")
    private String mailFrom;

    public String[] corsOrigins() {
        return corsAllowedOrigins.split("\\s*,\\s*");
    }
}
