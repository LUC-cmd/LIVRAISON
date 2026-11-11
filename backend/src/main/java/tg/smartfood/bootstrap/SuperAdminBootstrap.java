package tg.smartfood.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.smartfood.auth.AuthService;
import tg.smartfood.user.Role;
import tg.smartfood.user.User;
import tg.smartfood.user.UserRepository;

/** Cree le premier superadministrateur au demarrage s'il n'en existe aucun. */
@Component
@Order(1)
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final UserRepository users;
    private final AuthService authService;
    private final String email;
    private final String password;

    public SuperAdminBootstrap(UserRepository users, AuthService authService,
                               @Value("${smartfood.bootstrap.superadmin-email}") String email,
                               @Value("${smartfood.bootstrap.superadmin-password}") String password) {
        this.users = users;
        this.authService = authService;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!users.findByRole(Role.SUPERADMIN).isEmpty()) {
            return;
        }
        User user = authService.createUser("Super Administrateur", email, "+22890000000", password, Role.SUPERADMIN);
        log.warn("Superadministrateur cree : {}. Changez son mot de passe apres la premiere connexion.", user.getEmail());
    }
}
