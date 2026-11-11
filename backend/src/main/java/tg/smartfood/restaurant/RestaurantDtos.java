package tg.smartfood.restaurant;

import jakarta.validation.constraints.*;

import java.time.LocalTime;
import java.util.List;

public final class RestaurantDtos {

    private RestaurantDtos() {
    }

    public record DishDto(Long id, Long restaurantId, Long categoryId, String name, String description, int price,
                          SizeClass sizeClass, boolean available, List<ImageDto> images) {
    }

    public record ImageDto(Long id, String key, String url) {
    }

    public record CategoryDto(Long id, String name, int position, List<DishDto> dishes) {
    }

    public record HourDto(
            @Min(value = 1, message = "Jour invalide.") @Max(value = 7, message = "Jour invalide.") int dayOfWeek,
            @NotNull(message = "L'heure d'ouverture est obligatoire.") LocalTime opensAt,
            @NotNull(message = "L'heure de fermeture est obligatoire.") LocalTime closesAt) {
    }

    public record RestaurantCard(Long id, String name, String description, String cuisine, String logoUrl, String addressLine,
                                 String city, double latitude, double longitude, String phone, boolean accepting, boolean openNow) {
    }

    public record RestaurantDetail(RestaurantCard restaurant, List<HourDto> hours, List<CategoryDto> categories, List<DishDto> uncategorized) {
    }

    public record ProfileRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 120) String name,
            @Size(max = 1000, message = "La description est trop longue.") String description,
            @Size(max = 60) String cuisine,
            @Size(max = 200) String logoKey,
            @NotBlank(message = "L'adresse est obligatoire.") @Size(max = 255) String addressLine,
            @NotBlank(message = "La ville est obligatoire.") @Size(max = 80) String city,
            @NotNull(message = "La position est obligatoire.") @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull(message = "La position est obligatoire.") @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotBlank(message = "Le telephone est obligatoire.")
            @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Le telephone doit contenir 8 a 15 chiffres.") String phone,
            Boolean accepting) {
    }

    public record CategoryRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 80) String name,
            @Min(0) int position) {
    }

    public record DishRequest(
            @NotBlank(message = "Le nom est obligatoire.") @Size(max = 120) String name,
            @Size(max = 600, message = "La description est trop longue.") String description,
            @Min(value = 1, message = "Le prix doit etre positif.") @Max(value = 10_000_000, message = "Le prix est trop eleve.") int price,
            @NotNull(message = "Le gabarit est obligatoire.") SizeClass sizeClass,
            Long categoryId,
            Boolean available) {
    }

    public record AvailabilityRequest(boolean available) {
    }

    public record ImageRequest(@NotBlank(message = "La cle du fichier est obligatoire.") @Size(max = 200) String storageKey) {
    }
}
