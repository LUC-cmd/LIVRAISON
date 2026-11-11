package tg.smartfood.box;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tg.smartfood.box.BoxViews.BoxView;
import tg.smartfood.common.AuthUser;

import java.util.List;

/** Etat des coffres tel que le serveur le connait, filtre selon le role de l'utilisateur. */
@RestController
@RequestMapping("/api/v1/boxes")
public class BoxController {

    private final BoxService boxService;

    public BoxController(BoxService boxService) {
        this.boxService = boxService;
    }

    @GetMapping
    public List<BoxView> list(@AuthenticationPrincipal AuthUser user) {
        return boxService.visibleBoxes(user);
    }

    @GetMapping("/{id}")
    public BoxView get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return boxService.visibleBox(id, user);
    }
}
