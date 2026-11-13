package sn.chezketchup.backend.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sn.chezketchup.backend.security.JwtService;

@Service
public class AuthService {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Hash BCrypt d'une valeur aleatoire, calcule une fois (meme cout que les vrais). */
    private volatile String hashFactice;

    public AuthService(
            StaffUserRepository staffUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(String username, String password) {
        StaffUser user = compteVerifie(username, password);

        if (user.mustChangePassword()) {
            return new LoginResponse(null, user.getRole(), true);
        }

        return issueSession(user);
    }

    @Transactional
    public LoginResponse changeTemporaryPassword(String username, String temporaryPassword, String newPassword) {
        StaffUser user = compteVerifie(username, temporaryPassword);

        if (!user.mustChangePassword()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choisissez un mot de passe different du mot de passe temporaire."
            );
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        staffUserRepository.save(user);
        return issueSession(user);
    }

    /**
     * Compte actif dont le mot de passe correspond, sinon BadCredentialsException.
     * Le mot de passe est TOUJOURS verifie par BCrypt, meme quand l'identifiant
     * n'existe pas (hash factice) : le temps de reponse ne revele pas quels
     * identifiants existent (enumeration des comptes du personnel).
     */
    private StaffUser compteVerifie(String username, String password) {
        StaffUser user = staffUserRepository.findByUsernameIgnoreCase(username.trim())
                .filter(StaffUser::isActive)
                .orElse(null);
        String hash = user != null ? user.getPasswordHash() : obtenirHashFactice();
        boolean correspond = passwordEncoder.matches(password, hash);
        if (user == null || !correspond) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return user;
    }

    private String obtenirHashFactice() {
        String h = hashFactice;
        if (h == null) {
            h = passwordEncoder.encode(TemporaryPasswordGenerator.class.getName() + System.nanoTime());
            hashFactice = h;
        }
        return h;
    }

    private LoginResponse issueSession(StaffUser user) {
        return new LoginResponse(jwtService.generateToken(user.getUsername(), user.getRole()), user.getRole(), false);
    }
}