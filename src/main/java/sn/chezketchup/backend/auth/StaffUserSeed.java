package sn.chezketchup.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import sn.chezketchup.backend.security.Role;

@Component
@ConditionalOnProperty(name = "app.auth.seed.enabled", havingValue = "true")
public class StaffUserSeed implements CommandLineRunner {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.seed.cuisine.password}")
    private String cuisinePassword;

    @Value("${app.auth.seed.gerant.password}")
    private String gerantPassword;

    @Value("${app.auth.seed.manager.password}")
    private String managerPassword;

    @Value("${app.auth.seed.livreur.password}")
    private String livreurPassword;

    public StaffUserSeed(StaffUserRepository staffUserRepository, PasswordEncoder passwordEncoder) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        createIfMissing("cuisine", cuisinePassword, Role.CUISINE);
        createIfMissing("gerant", gerantPassword, Role.GERANT);
        createIfMissing("manager", managerPassword, Role.MANAGER);
        createIfMissing("livreur", livreurPassword, Role.LIVREUR);
    }

    private void createIfMissing(String username, String password, Role role) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Missing password for seeded user " + username);
        }
        if (staffUserRepository.findByUsernameIgnoreCase(username).isEmpty()) {
            staffUserRepository.save(new StaffUser(username, passwordEncoder.encode(password), role));
        }
    }
}