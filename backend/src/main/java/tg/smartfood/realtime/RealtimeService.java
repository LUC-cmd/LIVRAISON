package tg.smartfood.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pousse des evenements temps reel vers des utilisateurs precis (file privee /user/queue/events). */
@Service
public class RealtimeService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeService.class);

    private final SimpMessagingTemplate template;

    public RealtimeService(SimpMessagingTemplate template) {
        this.template = template;
    }

    /** L'envoi a lieu apres validation de la transaction, pour que le client relise des donnees a jour. */
    public void push(Long userId, String type, Object payload) {
        if (userId == null) {
            return;
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        message.put("payload", payload);
        Runnable send = () -> {
            try {
                template.convertAndSendToUser(String.valueOf(userId), "/queue/events", message);
            } catch (MessagingException e) {
                log.warn("Envoi temps reel impossible pour l'utilisateur {} : {}", userId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    public void pushAll(Collection<Long> userIds, String type, Object payload) {
        userIds.stream().distinct().forEach(id -> push(id, type, payload));
    }
}
