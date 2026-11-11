package tg.smartfood.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    List<User> findAllByOrderByIdDesc();

    @Query("select distinct u from User u join u.roles r where r = :role order by u.id")
    List<User> findByRole(@Param("role") Role role);

    @Query("select distinct u.id from User u join u.roles r where r in (tg.smartfood.user.Role.ADMIN, tg.smartfood.user.Role.SUPERADMIN) and u.status = tg.smartfood.user.UserStatus.ACTIVE")
    List<Long> findAdminIds();
}
