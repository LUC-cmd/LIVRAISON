package tg.smartfood.box;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.audit.AuditService;
import tg.smartfood.common.ApiException;
import tg.smartfood.common.AppProps;
import tg.smartfood.order.FoodOrder;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Codes de retrait : six chiffres aleatoires, a usage unique.
 * La verification se fait sur une empreinte HMAC-SHA256 ; une copie chiffree en AES-GCM
 * ne sert qu'a reafficher le code a son client.
 */
@Service
public class CodeService {

    private static final int IV_LENGTH = 12;

    private final WithdrawalCodeRepository codes;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec hmacKey;
    private final SecretKeySpec aesKey;
    private final long validityMinutes;

    public CodeService(WithdrawalCodeRepository codes, AuditService audit, AppProps props) {
        this.codes = codes;
        this.audit = audit;
        this.hmacKey = new SecretKeySpec(props.getCodeHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        byte[] aes = Base64.getDecoder().decode(props.getCodeAesKey());
        if (aes.length != 32) {
            throw new IllegalStateException("smartfood.codes.aes-key doit etre une cle de 32 octets encodee en base64.");
        }
        this.aesKey = new SecretKeySpec(aes, "AES");
        this.validityMinutes = props.getCodeValidityMinutes();
    }

    public String hash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponible", e);
        }
    }

    private String encrypt(String code) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(code.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement indisponible", e);
        }
    }

    private String decrypt(String stored) {
        try {
            byte[] in = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, in, 0, IV_LENGTH));
            return new String(cipher.doFinal(in, IV_LENGTH, in.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Dechiffrement impossible", e);
        }
    }

    /** Genere le code d'une commande verrouillee. Tout code actif precedent de la commande est revoque. */
    @Transactional
    public WithdrawalCode generate(FoodOrder order, Compartment compartment) {
        codes.findByOrderIdAndStatus(order.getId(), CodeStatus.ACTIVE).ifPresent(previous -> {
            previous.setStatus(CodeStatus.REVOKED);
            codes.saveAndFlush(previous);
        });
        for (int attempt = 0; attempt < 50; attempt++) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            String hash = hash(code);
            if (codes.existsByBoxIdAndCodeHashAndStatus(compartment.getBoxId(), hash, CodeStatus.ACTIVE)) {
                continue;
            }
            WithdrawalCode entity = new WithdrawalCode();
            entity.setOrderId(order.getId());
            entity.setCustomerId(order.getCustomerId());
            entity.setBoxId(compartment.getBoxId());
            entity.setCompartmentId(compartment.getId());
            entity.setCodeHash(hash);
            entity.setCodeEncrypted(encrypt(code));
            entity.setExpiresAt(Instant.now().plus(validityMinutes, ChronoUnit.MINUTES));
            return codes.save(entity);
        }
        throw new IllegalStateException("Impossible de generer un code unique pour le coffre " + compartment.getBoxId());
    }

    /** Reaffiche le code a son proprietaire. Tout autre utilisateur recoit une erreur 404. */
    @Transactional
    public RevealedCode reveal(FoodOrder order, Long customerId) {
        if (!order.getCustomerId().equals(customerId)) {
            throw ApiException.notFound("Commande introuvable.");
        }
        WithdrawalCode code = codes.findByOrderIdAndStatus(order.getId(), CodeStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Aucun code actif pour cette commande."));
        if (code.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.conflict("CODE_EXPIRED", "Ce code a expire. Demandez un nouveau code.");
        }
        audit.log(customerId, "CODE_REVEALED", "ORDER", order.getId(), null);
        return new RevealedCode(decrypt(code.getCodeEncrypted()), code.getExpiresAt());
    }

    public record RevealedCode(String code, Instant expiresAt) {
    }
}
