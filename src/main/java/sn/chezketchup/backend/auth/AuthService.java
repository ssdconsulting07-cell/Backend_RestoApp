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
        StaffUser user = staffUserRepository.findByUsernameIgnoreCase(username.trim())
                .filter(StaffUser::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (user.mustChangePassword()) {
            return new LoginResponse(null, user.getRole(), true);
        }

        return issueSession(user);
    }

    @Transactional
    public LoginResponse changeTemporaryPassword(String username, String temporaryPassword, String newPassword) {
        StaffUser user = staffUserRepository.findByUsernameIgnoreCase(username.trim())
                .filter(StaffUser::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!user.mustChangePassword() || !passwordEncoder.matches(temporaryPassword, user.getPasswordHash())) {
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

    private LoginResponse issueSession(StaffUser user) {
        return new LoginResponse(jwtService.generateToken(user.getUsername(), user.getRole()), user.getRole(), false);
    }
}