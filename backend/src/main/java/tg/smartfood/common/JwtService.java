package tg.smartfood.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import tg.smartfood.user.Role;
import tg.smartfood.user.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessMinutes;

    public JwtService(AppProps props) {
        byte[] secret = props.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("smartfood.jwt.secret doit contenir au moins 32 caracteres.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessMinutes = props.getAccessMinutes();
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = new ArrayList<>();
        user.getRoles().forEach(r -> roles.add(r.name()));
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** Retourne l'utilisateur du jeton, ou vide si le jeton est absent, invalide ou expire. */
    public Optional<AuthUser> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Set<Role> roles = EnumSet.noneOf(Role.class);
            Object raw = claims.get("roles");
            if (raw instanceof List<?> list) {
                for (Object r : list) {
                    roles.add(Role.valueOf(String.valueOf(r)));
                }
            }
            return Optional.of(new AuthUser(Long.valueOf(claims.getSubject()), claims.get("email", String.class), roles));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
