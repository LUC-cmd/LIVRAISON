package tg.smartfood.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tg.smartfood.user.Role;
import tg.smartfood.user.UserDto;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 120, message = "Le nom est trop long.") String fullName,
            @NotBlank(message = "L'email est obligatoire.") @Email(message = "L'email est invalide.") @Size(max = 160) String email,
            @NotBlank(message = "Le telephone est obligatoire.")
            @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Le telephone doit contenir 8 a 15 chiffres.") String phone,
            @NotBlank(message = "Le mot de passe est obligatoire.")
            @Size(min = 8, max = 72, message = "Le mot de passe doit contenir 8 a 72 caracteres.") String password,
            @NotNull(message = "Le type de compte est obligatoire.") Role role) {
    }

    public record LoginRequest(
            @NotBlank(message = "L'email est obligatoire.") String email,
            @NotBlank(message = "Le mot de passe est obligatoire.") String password) {
    }

    public record RefreshRequest(@NotBlank(message = "Le jeton est obligatoire.") String refreshToken) {
    }

    public record AuthResponse(String accessToken, String refreshToken, UserDto user) {
    }
}
