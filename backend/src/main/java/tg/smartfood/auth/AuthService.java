package tg.smartfood.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.audit.AuditService;
import tg.smartfood.auth.AuthDtos.AuthResponse;
import tg.smartfood.auth.AuthDtos.LoginRequest;
import tg.smartfood.auth.AuthDtos.RegisterRequest;
import tg.smartfood.common.ApiException;
import tg.smartfood.common.AppProps;
import tg.smartfood.common.Hashing;
import tg.smartfood.common.JwtService;
import tg.smartfood.notification.NotificationPreference;
import tg.smartfood.notification.NotificationPreferenceRepository;
import tg.smartfood.user.CourierProfile;
import tg.smartfood.user.CourierProfileRepository;
import tg.smartfood.user.RefreshToken;
import tg.smartfood.user.RefreshTokenRepository;
import tg.smartfood.user.Role;
import tg.smartfood.user.User;
import tg.smartfood.user.UserDto;
import tg.smartfood.user.UserRepository;
import tg.smartfood.user.UserStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Service
public class AuthService {

    private static final int MAX_FAILED_LOGINS = 5;
    private static final long LOCK_MINUTES = 15;
    private static final Set<Role> SELF_SERVICE_ROLES = Set.of(Role.CUSTOMER, Role.RESTAURANT, Role.COURIER);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final CourierProfileRepository courierProfiles;
    private final NotificationPreferenceRepository preferences;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;
    private final AppProps props;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, CourierProfileRepository courierProfiles,
                       NotificationPreferenceRepository preferences, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuditService audit, AppProps props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.courierProfiles = courierProfiles;
        this.preferences = preferences;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
        this.props = props;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!SELF_SERVICE_ROLES.contains(request.role())) {
            throw ApiException.forbidden("Ce type de compte ne peut pas etre cree par inscription.");
        }
        User user = createUser(request.fullName(), request.email(), request.phone(), request.password(), request.role());
        return issueTokens(user);
    }

    /** Creation de compte commune a l'inscription, au demarrage et a la creation d'administrateurs. */
    @Transactional
    public User createUser(String fullName, String email, String phone, String password, Role role) {
        String normalizedEmail = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw ApiException.conflict("EMAIL_TAKEN", "Un compte existe deja avec cet email.");
        }
        if (users.existsByPhone(phone.trim())) {
            throw ApiException.conflict("PHONE_TAKEN", "Un compte existe deja avec ce numero de telephone.");
        }
        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(normalizedEmail);
        user.setPhone(phone.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.getRoles().add(role);
        user = users.save(user);

        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(user.getId());
        preferences.save(preference);

        if (role == Role.COURIER) {
            CourierProfile profile = new CourierProfile();
            profile.setUserId(user.getId());
            courierProfiles.save(profile);
        }
        audit.log(user.getId(), "USER_CREATED", "USER", user.getId(), role.name());
        return user;
    }

    /** Les echecs sont comptes meme si la connexion est refusee : pas d'annulation sur ApiException. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        if (user == null) {
            audit.security("LOGIN_UNKNOWN_EMAIL", null, null, request.email());
            throw ApiException.unauthorized("BAD_CREDENTIALS", "Email ou mot de passe incorrect.");
        }
        Instant now = Instant.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED",
                    "Trop de tentatives. Reessayez dans quelques minutes.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            if (user.getFailedLoginCount() >= MAX_FAILED_LOGINS) {
                user.setLockedUntil(now.plus(LOCK_MINUTES, ChronoUnit.MINUTES));
                user.setFailedLoginCount(0);
                audit.security("LOGIN_LOCKED", user.getId(), null, "Compte bloque apres " + MAX_FAILED_LOGINS + " echecs");
            }
            users.save(user);
            throw ApiException.unauthorized("BAD_CREDENTIALS", "Email ou mot de passe incorrect.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw ApiException.forbidden("Ce compte est suspendu. Contactez l'assistance.");
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        users.save(user);
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(Hashing.sha256Hex(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH", "Session expiree. Reconnectez-vous."));
        if (stored.getRevokedAt() != null) {
            // Reutilisation d'un jeton deja echange : on ferme toutes les sessions du compte.
            revokeAll(stored.getUserId());
            audit.security("REFRESH_TOKEN_REUSE", stored.getUserId(), null, "Jeton de rafraichissement rejoue");
            throw ApiException.unauthorized("INVALID_REFRESH", "Session expiree. Reconnectez-vous.");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("INVALID_REFRESH", "Session expiree. Reconnectez-vous.");
        }
        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH", "Session expiree. Reconnectez-vous."));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw ApiException.forbidden("Ce compte est suspendu. Contactez l'assistance.");
        }
        stored.setRevokedAt(Instant.now());
        refreshTokens.save(stored);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(Hashing.sha256Hex(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokens.save(token);
            }
        });
    }

    @Transactional
    public void revokeAll(Long userId) {
        Instant now = Instant.now();
        refreshTokens.findByUserIdAndRevokedAtIsNull(userId).forEach(token -> {
            token.setRevokedAt(now);
            refreshTokens.save(token);
        });
    }

    private AuthResponse issueTokens(User user) {
        String raw = Hashing.randomToken(48);
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(Hashing.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(props.getRefreshDays(), ChronoUnit.DAYS));
        refreshTokens.save(token);
        return new AuthResponse(jwtService.createAccessToken(user), raw, UserDto.of(user));
    }
}
