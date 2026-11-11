package tg.smartfood.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.common.AppProps;
import tg.smartfood.realtime.RealtimeService;
import tg.smartfood.user.UserRepository;

import java.util.Collection;

/**
 * Notifications dans l'application (base + temps reel) et par email si un serveur SMTP est configure
 * (spring.mail.host) et que l'utilisateur l'a demande. SMS, WhatsApp et push s'ajoutent sur le meme modele.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final UserRepository users;
    private final RealtimeService realtime;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProps props;

    public NotificationService(NotificationRepository notifications, NotificationPreferenceRepository preferences,
                               UserRepository users, RealtimeService realtime,
                               ObjectProvider<JavaMailSender> mailSender, AppProps props) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.users = users;
        this.realtime = realtime;
        this.mailSender = mailSender;
        this.props = props;
    }

    @Transactional
    public void notify(Long userId, String type, String title, String body, String link) {
        if (userId == null) {
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        notifications.save(n);
        realtime.push(userId, "notification", n);
        sendEmailIfWanted(userId, title, body);
    }

    @Transactional
    public void notifyAll(Collection<Long> userIds, String type, String title, String body, String link) {
        userIds.stream().distinct().forEach(id -> notify(id, type, title, body, link));
    }

    @Transactional
    public void notifyAdmins(String type, String title, String body, String link) {
        notifyAll(users.findAdminIds(), type, title, body, link);
    }

    private void sendEmailIfWanted(Long userId, String title, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            return;
        }
        boolean wanted = preferences.findById(userId).map(NotificationPreference::isEmail).orElse(false);
        if (!wanted) {
            return;
        }
        users.findById(userId).ifPresent(user -> {
            try {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setFrom(props.getMailFrom());
                mail.setTo(user.getEmail());
                mail.setSubject("SmartFood - " + title);
                mail.setText(body);
                sender.send(mail);
            } catch (MailException e) {
                log.warn("Email non envoye a l'utilisateur {} : {}", userId, e.getMessage());
            }
        });
    }
}
