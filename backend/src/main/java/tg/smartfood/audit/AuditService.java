package tg.smartfood.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogs;
    private final SecurityEventRepository securityEvents;

    public AuditService(AuditLogRepository auditLogs, SecurityEventRepository securityEvents) {
        this.auditLogs = auditLogs;
        this.securityEvents = securityEvents;
    }

    /** Journalise une operation sensible dans la transaction en cours. */
    @Transactional
    public void log(Long actorId, String action, String entityType, Long entityId, String detail) {
        AuditLog entry = new AuditLog();
        entry.setActorId(actorId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetail(truncate(detail));
        auditLogs.save(entry);
    }

    /** Enregistre un evenement de securite dans sa propre transaction : il survit a l'annulation de l'operation refusee. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void security(String type, Long userId, Long boxId, String detail) {
        SecurityEvent event = new SecurityEvent();
        event.setType(type);
        event.setUserId(userId);
        event.setBoxId(boxId);
        event.setDetail(truncate(detail));
        securityEvents.save(event);
    }

    private static String truncate(String value) {
        return value != null && value.length() > 600 ? value.substring(0, 600) : value;
    }
}
