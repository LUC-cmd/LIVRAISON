package tg.smartfood.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.auth.AuthService;
import tg.smartfood.box.BoxService;
import tg.smartfood.restaurant.Category;
import tg.smartfood.restaurant.CategoryRepository;
import tg.smartfood.restaurant.Dish;
import tg.smartfood.restaurant.DishRepository;
import tg.smartfood.restaurant.Restaurant;
import tg.smartfood.restaurant.RestaurantHour;
import tg.smartfood.restaurant.RestaurantHourRepository;
import tg.smartfood.restaurant.RestaurantRepository;
import tg.smartfood.restaurant.RestaurantStatus;
import tg.smartfood.restaurant.SizeClass;
import tg.smartfood.user.Address;
import tg.smartfood.user.AddressRepository;
import tg.smartfood.user.CourierProfile;
import tg.smartfood.user.CourierProfileRepository;
import tg.smartfood.user.Role;
import tg.smartfood.user.User;
import tg.smartfood.user.UserRepository;

import java.time.LocalTime;
import java.util.List;

/**
 * Jeu de donnees de demonstration pour les tests (smartfood.demo-data=true) : comptes, restaurants de Lome,
 * plats, un livreur et un coffre a six compartiments. Desactive par le profil de production.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "smartfood.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String PASSWORD = "Demo1234!";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final AuthService authService;
    private final AddressRepository addresses;
    private final RestaurantRepository restaurants;
    private final RestaurantHourRepository hours;
    private final CategoryRepository categories;
    private final DishRepository dishes;
    private final CourierProfileRepository courierProfiles;
    private final BoxService boxService;

    public DemoDataSeeder(UserRepository users, AuthService authService, AddressRepository addresses, RestaurantRepository restaurants,
                          RestaurantHourRepository hours, CategoryRepository categories, DishRepository dishes,
                          CourierProfileRepository courierProfiles, BoxService boxService) {
        this.users = users;
        this.authService = authService;
        this.addresses = addresses;
        this.restaurants = restaurants;
        this.hours = hours;
        this.categories = categories;
        this.dishes = dishes;
        this.courierProfiles = courierProfiles;
        this.boxService = boxService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByEmailIgnoreCase("admin@demo.tg")) {
            return;
        }
        authService.createUser("Admin Demo", "admin@demo.tg", "+22890000001", PASSWORD, Role.ADMIN);

        User kossi = authService.createUser("Kossi Mensah", "kossi@demo.tg", "+22890000011", PASSWORD, Role.CUSTOMER);
        address(kossi, "Maison", "Rue des Hydrocarbures, Nyekonakpoe", 6.1290, 1.2060);
        User ama = authService.createUser("Ama Lawson", "ama@demo.tg", "+22890000012", PASSWORD, Role.CUSTOMER);
        address(ama, "Bureau", "Avenue de la Liberation, Hedzranawoe", 6.1700, 1.2450);

        User afi = authService.createUser("Afi Agbeko", "afi@demo.tg", "+22890000021", PASSWORD, Role.RESTAURANT);
        Restaurant chezAfi = restaurant(afi, "Chez Afi", "Cuisine togolaise familiale, preparee chaque matin.", "Togolaise",
                "Marche de Be", 6.1320, 1.2385, "+22890000021");
        Category afiPlats = category(chezAfi, "Plats", 0);
        Category afiBoissons = category(chezAfi, "Boissons et desserts", 1);
        dish(chezAfi, afiPlats, "Ayimolou", "Riz et haricots, sauce tomate pimentee, poisson frit.", 1000, SizeClass.MEDIUM);
        dish(chezAfi, afiPlats, "Akoume sauce ademe", "Pate de mais, sauce ademe au poisson fume.", 1500, SizeClass.MEDIUM);
        dish(chezAfi, afiPlats, "Gboma dessi", "Sauce d'epinards a la viande de boeuf, servie avec akoume.", 2000, SizeClass.MEDIUM);
        dish(chezAfi, afiBoissons, "Jus de bissap", "Bouteille de 33 cl, bien frais.", 500, SizeClass.SMALL);
        dish(chezAfi, afiBoissons, "Degue", "Yaourt au mil, pot de 25 cl.", 700, SizeClass.SMALL);

        User yao = authService.createUser("Yao Dossou", "yao@demo.tg", "+22890000022", PASSWORD, Role.RESTAURANT);
        Restaurant fufu = restaurant(yao, "Le Fufu d'Or", "Fufu pile a la main et sauces mijotees.", "Togolaise",
                "Rue de Tokoin Hopital", 6.1560, 1.2150, "+22890000022");
        Category fufuPlats = category(fufu, "Fufu", 0);
        Category fufuBoissons = category(fufu, "Boissons", 1);
        dish(fufu, fufuPlats, "Fufu sauce arachide", "Fufu d'igname, sauce arachide au poulet.", 2500, SizeClass.MEDIUM);
        dish(fufu, fufuPlats, "Fufu sauce claire", "Fufu d'igname, sauce claire au poisson frais.", 3000, SizeClass.MEDIUM);
        dish(fufu, fufuPlats, "Plateau familial", "Fufu pour quatre personnes, deux sauces au choix.", 8000, SizeClass.LARGE);
        dish(fufu, fufuBoissons, "Jus de gingembre", "Bouteille de 33 cl.", 500, SizeClass.SMALL);

        User edem = authService.createUser("Edem Kpodar", "edem@demo.tg", "+22890000023", PASSWORD, Role.RESTAURANT);
        Restaurant grill = restaurant(edem, "Grill du Boulevard", "Grillades au feu de bois, ouvertes tard le soir.", "Grillades",
                "Boulevard du 13 Janvier", 6.1300, 1.2200, "+22890000023");
        Category grillades = category(grill, "Grillades", 0);
        Category accompagnements = category(grill, "Accompagnements", 1);
        dish(grill, grillades, "Poulet braise entier", "Poulet bicyclette braise, piment et oignons.", 6000, SizeClass.LARGE);
        dish(grill, grillades, "Demi-poulet braise", "Demi-poulet braise, piment et oignons.", 3500, SizeClass.MEDIUM);
        dish(grill, grillades, "Brochettes de boeuf", "Quatre brochettes marinees.", 2000, SizeClass.MEDIUM);
        dish(grill, accompagnements, "Alloco", "Bananes plantain frites.", 1000, SizeClass.SMALL);
        dish(grill, accompagnements, "Koliko", "Frites d'igname.", 1000, SizeClass.SMALL);

        User koffi = authService.createUser("Koffi Amegan", "koffi@demo.tg", "+22890000031", PASSWORD, Role.COURIER);
        CourierProfile profile = courierProfiles.findById(koffi.getId()).orElseThrow();
        profile.setAvailable(true);
        courierProfiles.save(profile);

        boxService.createBox("Coffre Moto 01", "SFD-0001",
                List.of(SizeClass.SMALL, SizeClass.SMALL, SizeClass.MEDIUM, SizeClass.MEDIUM, SizeClass.MEDIUM, SizeClass.LARGE),
                koffi.getId(), null);

        log.info("Donnees de demonstration creees. Comptes : admin@demo.tg, kossi@demo.tg, ama@demo.tg, afi@demo.tg, "
                + "yao@demo.tg, edem@demo.tg, koffi@demo.tg - mot de passe {}", PASSWORD);
    }

    private void address(User user, String label, String line, double latitude, double longitude) {
        Address address = new Address();
        address.setUserId(user.getId());
        address.setLabel(label);
        address.setLine(line);
        address.setCity("Lome");
        address.setLatitude(latitude);
        address.setLongitude(longitude);
        address.setDefaultAddress(true);
        addresses.save(address);
    }

    private Restaurant restaurant(User owner, String name, String description, String cuisine, String line,
                                  double latitude, double longitude, String phone) {
        Restaurant restaurant = new Restaurant();
        restaurant.setOwnerId(owner.getId());
        restaurant.setName(name);
        restaurant.setDescription(description);
        restaurant.setCuisine(cuisine);
        restaurant.setAddressLine(line);
        restaurant.setCity("Lome");
        restaurant.setLatitude(latitude);
        restaurant.setLongitude(longitude);
        restaurant.setPhone(phone);
        restaurant.setStatus(RestaurantStatus.APPROVED);
        Restaurant saved = restaurants.save(restaurant);
        for (int day = 1; day <= 7; day++) {
            RestaurantHour hour = new RestaurantHour();
            hour.setRestaurantId(saved.getId());
            hour.setDayOfWeek(day);
            hour.setOpensAt(LocalTime.of(0, 0));
            hour.setClosesAt(LocalTime.of(23, 59, 59));
            hours.save(hour);
        }
        return saved;
    }

    private Category category(Restaurant restaurant, String name, int position) {
        Category category = new Category();
        category.setRestaurantId(restaurant.getId());
        category.setName(name);
        category.setPosition(position);
        return categories.save(category);
    }

    private void dish(Restaurant restaurant, Category category, String name, String description, int price, SizeClass size) {
        Dish dish = new Dish();
        dish.setRestaurantId(restaurant.getId());
        dish.setCategoryId(category.getId());
        dish.setName(name);
        dish.setDescription(description);
        dish.setPrice(price);
        dish.setSizeClass(size);
        dishes.save(dish);
    }
}
