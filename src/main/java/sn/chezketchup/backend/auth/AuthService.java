package sn.chezketchup.backend.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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

        return new LoginResponse(jwtService.generateToken(user.getUsername(), user.getRole()), user.getRole());
    }
}