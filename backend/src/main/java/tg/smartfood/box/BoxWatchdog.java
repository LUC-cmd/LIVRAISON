package tg.smartfood.box;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Surveillance periodique : ouvertures non confirmees par les capteurs et codes expires. */
@Component
public class BoxWatchdog {

    private static final Logger log = LoggerFactory.getLogger(BoxWatchdog.class);
    private static final Duration GRACE = Duration.ofMinutes(2);

    private final BoxService boxService;

    public BoxWatchdog(BoxService boxService) {
        this.boxService = boxService;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void run() {
        int incidents = boxService.runWatchdog(GRACE);
        if (incidents > 0) {
            log.warn("Surveillance des coffres : {} incident(s) capteur detecte(s).", incidents);
        }
    }
}
