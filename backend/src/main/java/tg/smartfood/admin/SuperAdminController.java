package tg.smartfood.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tg.smartfood.audit.AuditLog;
import tg.smartfood.audit.AuditLogRepository;
import tg.smartfood.audit.AuditService;
import tg.smartfood.auth.AuthService;
import tg.smartfood.common.ApiException;
import tg.smartfood.common.AppProps;
import tg.smartfood.common.AuthUser;
import tg.smartfood.user.Role;
import tg.smartfood.user.User;
import tg.smartfood.user.UserDto;
import tg.smartfood.user.UserRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Espace superadministrateur : comptes administrateurs, permissions, journal d'audit, parametres generaux. */
@RestController
@RequestMapping("/api/v1/superadmin")
public class SuperAdminController {

    public record CreateAdminRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 120) String fullName,
            @NotBlank(message = "L'email est obligatoire.") @Email(message = "L'email est invalide.") String email,
            @NotBlank(message = "Le telephone est obligatoire.")
            @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Le telephone doit contenir 8 a 15 chiffres.") String phone,
            @NotBlank @Size(min = 10, max = 72, message = "Le mot de passe doit contenir 10 a 72 caracteres.") String password) {
    }

    public record AdminRoleRequest(boolean admin) {
    }

    private final AuthService authService;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;
    private final AppProps props;

    public SuperAdminController(AuthService authService, UserRepository users, AuditLogRepository auditLogs, AuditService audit, AppProps props) {
        this.authService = authService;
        this.users = users;
        this.auditLogs = auditLogs;
        this.audit = audit;
        this.props = props;
    }

    @GetMapping("/admins")
    public List<UserDto> admins() {
        return users.findByRole(Role.ADMIN).stream().map(UserDto::of).toList();
    }

    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserDto createAdmin(@AuthenticationPrincipal AuthUser superAdmin, @Valid @RequestBody CreateAdminRequest request) {
        User user = authService.createUser(request.fullName(), request.email(), request.phone(), request.password(), Role.ADMIN);
        audit.log(superAdmin.id(), "ADMIN_CREATED", "USER", user.getId(), user.getEmail());
        return UserDto.of(user);
    }

    /** Attribue ou retire la permission d'administration a un compte existant. */
    @PutMapping("/users/{id}/admin")
    @Transactional
    public UserDto setAdmin(@AuthenticationPrincipal AuthUser superAdmin, @PathVariable Long id, @RequestBody AdminRoleRequest request) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("Compte introuvable."));
        if (user.getRoles().contains(Role.SUPERADMIN)) {
            throw ApiException.forbidden("Les permissions d'un superadministrateur ne se modifient pas ici.");
        }
        if (request.admin()) {
            user.getRoles().add(Role.ADMIN);
        } else {
            user.getRoles().remove(Role.ADMIN);
            if (user.getRoles().isEmpty()) {
                throw ApiException.badRequest("NO_ROLE", "Ce compte n'aurait plus aucun role : suspendez-le plutot.");
            }
        }
        users.save(user);
        authService.revokeAll(user.getId());
        audit.log(superAdmin.id(), request.admin() ? "ADMIN_GRANTED" : "ADMIN_REVOKED", "USER", id, user.getEmail());
        return UserDto.of(user);
    }

    @GetMapping("/audit-logs")
    public List<AuditLog> auditLogs() {
        return auditLogs.findTop300ByOrderByIdDesc();
    }

    /** Parametres generaux en vigueur. Ils se modifient par variables d'environnement (voir README), puis redemarrage. */
    @GetMapping("/settings")
    public Map<String, Object> settings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("deliveryFee", props.getDeliveryFee());
        settings.put("commissionPercent", props.getCommissionPercent());
        settings.put("courierSharePercent", props.getCourierSharePercent());
        settings.put("codeValidityMinutes", props.getCodeValidityMinutes());
        settings.put("codeMaxAttempts", props.getCodeMaxAttempts());
        settings.put("codeLockMinutes", props.getCodeLockMinutes());
        settings.put("loadingAuthorizationMinutes", props.getLoadingAuthorizationMinutes());
        settings.put("simulatorEnabled", props.isSimulatorEnabled());
        settings.put("paymentsSandboxEnabled", props.isPaymentsSandboxEnabled());
        return settings;
    }
}
