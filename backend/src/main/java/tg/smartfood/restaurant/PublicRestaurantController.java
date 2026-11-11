package tg.smartfood.restaurant;

import org.springframework.web.bind.annotation.*;
import tg.smartfood.restaurant.RestaurantDtos.RestaurantCard;
import tg.smartfood.restaurant.RestaurantDtos.RestaurantDetail;

import java.util.List;

/** Catalogue public : restaurants valides et leurs menus. */
@RestController
@RequestMapping("/api/v1/restaurants")
public class PublicRestaurantController {

    private final RestaurantService service;

    public PublicRestaurantController(RestaurantService service) {
        this.service = service;
    }

    @GetMapping
    public List<RestaurantCard> list(@RequestParam(required = false) String q, @RequestParam(required = false) String cuisine) {
        return service.search(q, cuisine);
    }

    @GetMapping("/{id}")
    public RestaurantDetail detail(@PathVariable Long id) {
        return service.publicDetail(id);
    }
}
