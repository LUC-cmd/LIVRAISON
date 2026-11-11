package tg.smartfood.restaurant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.common.ApiException;
import tg.smartfood.restaurant.RestaurantDtos.*;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RestaurantService {

    /** Fuseau de reference de la plateforme (Togo). */
    public static final ZoneId ZONE = ZoneId.of("Africa/Lome");

    private final RestaurantRepository restaurants;
    private final RestaurantHourRepository hours;
    private final CategoryRepository categories;
    private final DishRepository dishes;
    private final DishImageRepository images;

    public RestaurantService(RestaurantRepository restaurants, RestaurantHourRepository hours, CategoryRepository categories,
                             DishRepository dishes, DishImageRepository images) {
        this.restaurants = restaurants;
        this.hours = hours;
        this.categories = categories;
        this.dishes = dishes;
        this.images = images;
    }

    public static String fileUrl(String key) {
        return key == null || key.isBlank() ? null : "/api/v1/files/" + key;
    }

    /** Ouvert si le restaurant accepte les commandes et que l'heure courante tombe dans ses horaires (aucun horaire = pas de restriction). */
    public boolean isOpenNow(Restaurant restaurant) {
        if (!restaurant.isAccepting()) {
            return false;
        }
        List<RestaurantHour> all = hours.findByRestaurantIdOrderByDayOfWeekAsc(restaurant.getId());
        if (all.isEmpty()) {
            return true;
        }
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        int day = now.getDayOfWeek().getValue();
        LocalTime time = now.toLocalTime();
        return all.stream().anyMatch(h -> h.getDayOfWeek() == day && !time.isBefore(h.getOpensAt()) && !time.isAfter(h.getClosesAt()));
    }

    public RestaurantCard card(Restaurant r) {
        return new RestaurantCard(r.getId(), r.getName(), r.getDescription(), r.getCuisine(), fileUrl(r.getLogoKey()),
                r.getAddressLine(), r.getCity(), r.getLatitude(), r.getLongitude(), r.getPhone(), r.isAccepting(), isOpenNow(r));
    }

    @Transactional(readOnly = true)
    public List<RestaurantCard> search(String query, String cuisine) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String c = cuisine == null ? "" : cuisine.trim().toLowerCase(Locale.ROOT);
        return restaurants.findByStatusOrderByNameAsc(RestaurantStatus.APPROVED).stream()
                .filter(r -> q.isEmpty() || r.getName().toLowerCase(Locale.ROOT).contains(q)
                        || r.getDescription().toLowerCase(Locale.ROOT).contains(q)
                        || r.getCuisine().toLowerCase(Locale.ROOT).contains(q))
                .filter(r -> c.isEmpty() || r.getCuisine().toLowerCase(Locale.ROOT).equals(c))
                .map(this::card)
                .toList();
    }

    @Transactional(readOnly = true)
    public RestaurantDetail publicDetail(Long id) {
        Restaurant restaurant = restaurants.findById(id)
                .filter(r -> r.getStatus() == RestaurantStatus.APPROVED)
                .orElseThrow(() -> ApiException.notFound("Restaurant introuvable."));
        return detail(restaurant);
    }

    @Transactional(readOnly = true)
    public RestaurantDetail detail(Restaurant restaurant) {
        List<DishDto> all = dishDtos(dishes.findByRestaurantIdAndArchivedFalseOrderByIdAsc(restaurant.getId()));
        Map<Long, List<DishDto>> byCategory = new LinkedHashMap<>();
        List<DishDto> uncategorized = new ArrayList<>();
        for (DishDto dish : all) {
            if (dish.categoryId() == null) {
                uncategorized.add(dish);
            } else {
                byCategory.computeIfAbsent(dish.categoryId(), k -> new ArrayList<>()).add(dish);
            }
        }
        List<CategoryDto> categoryDtos = categories.findByRestaurantIdOrderByPositionAscIdAsc(restaurant.getId()).stream()
                .map(c -> new CategoryDto(c.getId(), c.getName(), c.getPosition(), byCategory.getOrDefault(c.getId(), List.of())))
                .toList();
        List<HourDto> hourDtos = hours.findByRestaurantIdOrderByDayOfWeekAsc(restaurant.getId()).stream()
                .map(h -> new HourDto(h.getDayOfWeek(), h.getOpensAt(), h.getClosesAt()))
                .toList();
        return new RestaurantDetail(card(restaurant), hourDtos, categoryDtos, uncategorized);
    }

    public List<DishDto> dishDtos(List<Dish> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ImageDto>> imagesByDish = new LinkedHashMap<>();
        for (DishImage image : images.findByDishIdInOrderByPositionAscIdAsc(list.stream().map(Dish::getId).toList())) {
            imagesByDish.computeIfAbsent(image.getDishId(), k -> new ArrayList<>())
                    .add(new ImageDto(image.getId(), image.getStorageKey(), fileUrl(image.getStorageKey())));
        }
        return list.stream()
                .map(d -> new DishDto(d.getId(), d.getRestaurantId(), d.getCategoryId(), d.getName(), d.getDescription(), d.getPrice(),
                        d.getSizeClass(), d.isAvailable(), imagesByDish.getOrDefault(d.getId(), List.of())))
                .toList();
    }

    public Restaurant requireOwned(Long ownerId) {
        return restaurants.findByOwnerId(ownerId)
                .orElseThrow(() -> ApiException.notFound("Vous n'avez pas encore cree votre restaurant."));
    }
}
