package tg.smartfood.restaurant;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tg.smartfood.audit.AuditService;
import tg.smartfood.common.ApiException;
import tg.smartfood.common.AuthUser;
import tg.smartfood.notification.NotificationService;
import tg.smartfood.restaurant.RestaurantDtos.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Espace restaurant : profil, horaires, categories et plats. Chaque requete est limitee au restaurant du compte connecte. */
@RestController
@RequestMapping("/api/v1/restaurant")
public class RestaurantManageController {

    private final RestaurantService service;
    private final RestaurantRepository restaurants;
    private final RestaurantHourRepository hours;
    private final CategoryRepository categories;
    private final DishRepository dishes;
    private final DishImageRepository images;
    private final NotificationService notifications;
    private final AuditService audit;

    public RestaurantManageController(RestaurantService service, RestaurantRepository restaurants, RestaurantHourRepository hours,
                                      CategoryRepository categories, DishRepository dishes, DishImageRepository images,
                                      NotificationService notifications, AuditService audit) {
        this.service = service;
        this.restaurants = restaurants;
        this.hours = hours;
        this.categories = categories;
        this.dishes = dishes;
        this.images = images;
        this.notifications = notifications;
        this.audit = audit;
    }

    public record ProfileView(Restaurant restaurant, String logoUrl, boolean openNow) {
    }

    private ProfileView view(Restaurant restaurant) {
        return new ProfileView(restaurant, RestaurantService.fileUrl(restaurant.getLogoKey()), service.isOpenNow(restaurant));
    }

    @GetMapping("/profile")
    public ProfileView profile(@AuthenticationPrincipal AuthUser auth) {
        return view(service.requireOwned(auth.id()));
    }

    /** Cree le restaurant (en attente de validation) ou met a jour son profil. */
    @PutMapping("/profile")
    @Transactional
    public ProfileView saveProfile(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody ProfileRequest request) {
        Restaurant restaurant = restaurants.findByOwnerId(auth.id()).orElse(null);
        boolean created = restaurant == null;
        if (created) {
            restaurant = new Restaurant();
            restaurant.setOwnerId(auth.id());
        }
        restaurant.setName(request.name().trim());
        restaurant.setDescription(request.description() == null ? "" : request.description().trim());
        restaurant.setCuisine(request.cuisine() == null ? "" : request.cuisine().trim());
        restaurant.setLogoKey(request.logoKey());
        restaurant.setAddressLine(request.addressLine().trim());
        restaurant.setCity(request.city().trim());
        restaurant.setLatitude(request.latitude());
        restaurant.setLongitude(request.longitude());
        restaurant.setPhone(request.phone().trim());
        if (request.accepting() != null) {
            restaurant.setAccepting(request.accepting());
        }
        restaurant = restaurants.save(restaurant);
        if (created) {
            audit.log(auth.id(), "RESTAURANT_CREATED", "RESTAURANT", restaurant.getId(), restaurant.getName());
            notifications.notifyAdmins("RESTAURANT_PENDING", "Restaurant a valider",
                    restaurant.getName() + " attend votre validation.", "/admin/restaurants");
        }
        return view(restaurant);
    }

    @GetMapping("/hours")
    public List<HourDto> getHours(@AuthenticationPrincipal AuthUser auth) {
        Restaurant restaurant = service.requireOwned(auth.id());
        return hours.findByRestaurantIdOrderByDayOfWeekAsc(restaurant.getId()).stream()
                .map(h -> new HourDto(h.getDayOfWeek(), h.getOpensAt(), h.getClosesAt()))
                .toList();
    }

    @PutMapping("/hours")
    @Transactional
    public List<HourDto> saveHours(@AuthenticationPrincipal AuthUser auth, @RequestBody List<HourDto> request) {
        Restaurant restaurant = service.requireOwned(auth.id());
        Set<Integer> days = new HashSet<>();
        for (HourDto hour : request) {
            if (hour.dayOfWeek() < 1 || hour.dayOfWeek() > 7 || hour.opensAt() == null || hour.closesAt() == null) {
                throw ApiException.badRequest("BAD_HOURS", "Chaque horaire doit indiquer un jour (1 a 7), une ouverture et une fermeture.");
            }
            if (!days.add(hour.dayOfWeek())) {
                throw ApiException.badRequest("DUPLICATE_DAY", "Un jour apparait deux fois dans les horaires.");
            }
            if (!hour.closesAt().isAfter(hour.opensAt())) {
                throw ApiException.badRequest("BAD_HOURS", "L'heure de fermeture doit suivre l'heure d'ouverture.");
            }
        }
        hours.deleteByRestaurantId(restaurant.getId());
        hours.flush();
        for (HourDto hour : request) {
            RestaurantHour entity = new RestaurantHour();
            entity.setRestaurantId(restaurant.getId());
            entity.setDayOfWeek(hour.dayOfWeek());
            entity.setOpensAt(hour.opensAt());
            entity.setClosesAt(hour.closesAt());
            hours.save(entity);
        }
        return getHours(auth);
    }

    @GetMapping("/categories")
    public List<Category> listCategories(@AuthenticationPrincipal AuthUser auth) {
        return categories.findByRestaurantIdOrderByPositionAscIdAsc(service.requireOwned(auth.id()).getId());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Category createCategory(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody CategoryRequest request) {
        Category category = new Category();
        category.setRestaurantId(service.requireOwned(auth.id()).getId());
        category.setName(request.name().trim());
        category.setPosition(request.position());
        return categories.save(category);
    }

    @PutMapping("/categories/{id}")
    @Transactional
    public Category updateCategory(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        Category category = ownedCategory(auth, id);
        category.setName(request.name().trim());
        category.setPosition(request.position());
        return categories.save(category);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteCategory(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        categories.delete(ownedCategory(auth, id));
    }

    private Category ownedCategory(AuthUser auth, Long id) {
        return categories.findByIdAndRestaurantId(id, service.requireOwned(auth.id()).getId())
                .orElseThrow(() -> ApiException.notFound("Categorie introuvable."));
    }

    @GetMapping("/dishes")
    public List<DishDto> listDishes(@AuthenticationPrincipal AuthUser auth) {
        return service.dishDtos(dishes.findByRestaurantIdAndArchivedFalseOrderByIdAsc(service.requireOwned(auth.id()).getId()));
    }

    @PostMapping("/dishes")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public DishDto createDish(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody DishRequest request) {
        Dish dish = new Dish();
        dish.setRestaurantId(service.requireOwned(auth.id()).getId());
        return service.dishDtos(List.of(applyDish(dish, request))).get(0);
    }

    @PutMapping("/dishes/{id}")
    @Transactional
    public DishDto updateDish(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id, @Valid @RequestBody DishRequest request) {
        return service.dishDtos(List.of(applyDish(ownedDish(auth, id), request))).get(0);
    }

    @PatchMapping("/dishes/{id}/availability")
    @Transactional
    public DishDto setAvailability(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id, @RequestBody AvailabilityRequest request) {
        Dish dish = ownedDish(auth, id);
        dish.setAvailable(request.available());
        return service.dishDtos(List.of(dishes.save(dish))).get(0);
    }

    /** Un plat deja commande n'est jamais supprime : il est archive pour conserver l'historique. */
    @DeleteMapping("/dishes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void archiveDish(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        Dish dish = ownedDish(auth, id);
        dish.setArchived(true);
        dish.setAvailable(false);
        dishes.save(dish);
    }

    @PostMapping("/dishes/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public DishDto addImage(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id, @Valid @RequestBody ImageRequest request) {
        Dish dish = ownedDish(auth, id);
        if (!request.storageKey().startsWith("dishes/")) {
            throw ApiException.badRequest("BAD_FILE_KEY", "La cle du fichier est invalide.");
        }
        DishImage image = new DishImage();
        image.setDishId(dish.getId());
        image.setStorageKey(request.storageKey());
        image.setPosition(images.findByDishIdOrderByPositionAscIdAsc(dish.getId()).size());
        images.save(image);
        return service.dishDtos(List.of(dish)).get(0);
    }

    @DeleteMapping("/dishes/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteImage(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id, @PathVariable Long imageId) {
        Dish dish = ownedDish(auth, id);
        images.delete(images.findByIdAndDishId(imageId, dish.getId())
                .orElseThrow(() -> ApiException.notFound("Image introuvable.")));
    }

    private Dish ownedDish(AuthUser auth, Long id) {
        return dishes.findByIdAndRestaurantId(id, service.requireOwned(auth.id()).getId())
                .filter(d -> !d.isArchived())
                .orElseThrow(() -> ApiException.notFound("Plat introuvable."));
    }

    private Dish applyDish(Dish dish, DishRequest request) {
        if (request.categoryId() != null) {
            categories.findByIdAndRestaurantId(request.categoryId(), dish.getRestaurantId())
                    .orElseThrow(() -> ApiException.badRequest("BAD_CATEGORY", "Cette categorie n'appartient pas a votre restaurant."));
        }
        dish.setCategoryId(request.categoryId());
        dish.setName(request.name().trim());
        dish.setDescription(request.description() == null ? "" : request.description().trim());
        dish.setPrice(request.price());
        dish.setSizeClass(request.sizeClass());
        if (request.available() != null) {
            dish.setAvailable(request.available());
        }
        return dishes.save(dish);
    }
}
