package tg.smartfood.notification;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tg.smartfood.common.ApiException;
import tg.smartfood.common.AuthUser;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal AuthUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unread", notifications.countByUserIdAndReadAtIsNull(user.id()));
        result.put("items", notifications.findTop100ByUserIdOrderByIdDesc(user.id()));
        return result;
    }

    @PostMapping("/{id}/read")
    @Transactional
    public Notification read(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        Notification n = notifications.findByIdAndUserId(id, user.id())
                .orElseThrow(() -> ApiException.notFound("Notification introuvable."));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
        }
        return notifications.save(n);
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Object> readAll(@AuthenticationPrincipal AuthUser user) {
        List<Notification> unread = notifications.findByUserIdAndReadAtIsNull(user.id());
        Instant now = Instant.now();
        unread.forEach(n -> n.setReadAt(now));
        notifications.saveAll(unread);
        return Map.of("updated", unread.size());
    }
}
