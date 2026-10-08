package sn.chezketchup.backend.auth;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import sn.chezketchup.backend.security.Role;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {

    Optional<StaffUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByRole(Role role);

    List<StaffUser> findAllByOrderByUsernameAsc();

    long countByRoleAndActiveTrue(Role role);
}