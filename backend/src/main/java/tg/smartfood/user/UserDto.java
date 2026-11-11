package tg.smartfood.user;

import java.time.Instant;
import java.util.Set;

/** Vue publique d'un compte : jamais de mot de passe ni de compteur de securite. */
public record UserDto(Long id, String email, String phone, String fullName, Set<Role> roles, UserStatus status, Instant createdAt) {

    public static UserDto of(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getPhone(), u.getFullName(), Set.copyOf(u.getRoles()), u.getStatus(), u.getCreatedAt());
    }
}
