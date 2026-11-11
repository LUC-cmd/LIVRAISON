package tg.smartfood.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tg.smartfood.common.ApiException;
import tg.smartfood.common.AuthUser;
import tg.smartfood.notification.NotificationPreference;
import tg.smartfood.notification.NotificationPreferenceRepository;

import java.util.List;

/** Profil, adresses et preferences du compte connecte. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    public record ProfileRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 120) String fullName,
            @NotBlank(message = "Le telephone est obligatoire.")
            @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Le telephone doit contenir 8 a 15 chiffres.") String phone) {
    }

    public record PasswordRequest(
            @NotBlank(message = "Le mot de passe actuel est obligatoire.") String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "Le mot de passe doit contenir 8 a 72 caracteres.") String newPassword) {
    }

    public record AddressRequest(
            @NotBlank(message = "Le libelle est obligatoire.") @Size(max = 60) String label,
            @NotBlank(message = "L'adresse est obligatoire.") @Size(max = 255) String line,
            @NotBlank(message = "La ville est obligatoire.") @Size(max = 80) String city,
            @NotNull(message = "La position est obligatoire.") @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull(message = "La position est obligatoire.") @DecimalMin("-180") @DecimalMax("180") Double longitude,
            boolean defaultAddress) {
    }

    public record PreferenceRequest(boolean inApp, boolean email, boolean sms, boolean push) {
    }

    private final UserRepository users;
    private final AddressRepository addresses;
    private final NotificationPreferenceRepository preferences;
    private final PasswordEncoder passwordEncoder;

    public MeController(UserRepository users, AddressRepository addresses, NotificationPreferenceRepository preferences,
                        PasswordEncoder passwordEncoder) {
        this.users = users;
        this.addresses = addresses;
        this.preferences = preferences;
        this.passwordEncoder = passwordEncoder;
    }

    private User load(AuthUser auth) {
        return users.findById(auth.id()).orElseThrow(() -> ApiException.notFound("Compte introuvable."));
    }

    @GetMapping
    public UserDto me(@AuthenticationPrincipal AuthUser auth) {
        return UserDto.of(load(auth));
    }

    @PutMapping
    @Transactional
    public UserDto update(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody ProfileRequest request) {
        User user = load(auth);
        String phone = request.phone().trim();
        if (!phone.equals(user.getPhone()) && users.existsByPhone(phone)) {
            throw ApiException.conflict("PHONE_TAKEN", "Un compte existe deja avec ce numero de telephone.");
        }
        user.setFullName(request.fullName().trim());
        user.setPhone(phone);
        return UserDto.of(users.save(user));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void changePassword(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody PasswordRequest request) {
        User user = load(auth);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("BAD_PASSWORD", "Le mot de passe actuel est incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
    }

    @GetMapping("/addresses")
    public List<Address> listAddresses(@AuthenticationPrincipal AuthUser auth) {
        return addresses.findByUserIdOrderByIdDesc(auth.id());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Address createAddress(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody AddressRequest request) {
        Address address = new Address();
        address.setUserId(auth.id());
        return saveAddress(address, request, addresses.findByUserIdOrderByIdDesc(auth.id()).isEmpty());
    }

    @PutMapping("/addresses/{id}")
    @Transactional
    public Address updateAddress(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id, @Valid @RequestBody AddressRequest request) {
        Address address = addresses.findByIdAndUserId(id, auth.id()).orElseThrow(() -> ApiException.notFound("Adresse introuvable."));
        return saveAddress(address, request, false);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteAddress(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        Address address = addresses.findByIdAndUserId(id, auth.id()).orElseThrow(() -> ApiException.notFound("Adresse introuvable."));
        addresses.delete(address);
    }

    private Address saveAddress(Address address, AddressRequest request, boolean forceDefault) {
        address.setLabel(request.label().trim());
        address.setLine(request.line().trim());
        address.setCity(request.city().trim());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
        boolean makeDefault = request.defaultAddress() || forceDefault;
        if (makeDefault) {
            addresses.findByUserIdOrderByIdDesc(address.getUserId()).forEach(other -> {
                if (other.isDefaultAddress() && !other.getId().equals(address.getId())) {
                    other.setDefaultAddress(false);
                    addresses.save(other);
                }
            });
        }
        address.setDefaultAddress(makeDefault);
        return addresses.save(address);
    }

    @GetMapping("/notification-preferences")
    public NotificationPreference getPreferences(@AuthenticationPrincipal AuthUser auth) {
        return preferences.findById(auth.id()).orElseGet(() -> {
            NotificationPreference created = new NotificationPreference();
            created.setUserId(auth.id());
            return created;
        });
    }

    @PutMapping("/notification-preferences")
    @Transactional
    public NotificationPreference updatePreferences(@AuthenticationPrincipal AuthUser auth, @RequestBody PreferenceRequest request) {
        NotificationPreference preference = getPreferences(auth);
        preference.setInApp(request.inApp());
        preference.setEmail(request.email());
        preference.setSms(request.sms());
        preference.setPush(request.push());
        return preferences.save(preference);
    }
}
