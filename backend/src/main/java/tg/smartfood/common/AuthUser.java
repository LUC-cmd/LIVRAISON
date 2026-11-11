package tg.smartfood.common;

import tg.smartfood.user.Role;

import java.security.Principal;
import java.util.Set;

/** Utilisateur authentifie, extrait du jeton JWT. */
public record AuthUser(Long id, String email, Set<Role> roles) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(id);
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN) || roles.contains(Role.SUPERADMIN);
    }
}
