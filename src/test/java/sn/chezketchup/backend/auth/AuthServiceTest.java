package sn.chezketchup.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import sn.chezketchup.backend.security.JwtService;
import sn.chezketchup.backend.security.Role;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private StaffUserRepository staffUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void returnsTokenAndRoleForValidCredentials() {
        StaffUser user = new StaffUser("cuisine", "encoded-password", Role.CUISINE);
        when(staffUserRepository.findByUsernameIgnoreCase("cuisine")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(jwtService.generateToken("cuisine", Role.CUISINE)).thenReturn("jwt-token");

        LoginResponse response = authService.login(" cuisine ", "password");

        assertEquals("jwt-token", response.token());
        assertEquals(Role.CUISINE, response.role());
        assertFalse(response.mustChangePassword());
    }

    @Test
    void doesNotIssueTokenForTemporaryPasswordLogin() {
        StaffUser user = new StaffUser("cuisine", "encoded-password", Role.CUISINE, true);
        when(staffUserRepository.findByUsernameIgnoreCase("cuisine")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("temporary-password", "encoded-password")).thenReturn(true);

        LoginResponse response = authService.login("cuisine", "temporary-password");

        assertNull(response.token());
        assertEquals(Role.CUISINE, response.role());
        assertTrue(response.mustChangePassword());
        }

        @Test
        void changesTemporaryPasswordBeforeIssuingSession() {
        StaffUser user = new StaffUser("cuisine", "encoded-temporary", Role.CUISINE, true);
        when(staffUserRepository.findByUsernameIgnoreCase("cuisine")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("temporary-password", "encoded-temporary")).thenReturn(true);
        when(passwordEncoder.matches("new-secure-password", "encoded-temporary")).thenReturn(false);
        when(passwordEncoder.encode("new-secure-password")).thenReturn("encoded-new-password");
        when(staffUserRepository.save(user)).thenReturn(user);
        when(jwtService.generateToken("cuisine", Role.CUISINE)).thenReturn("jwt-token");

        LoginResponse response = authService.changeTemporaryPassword(
            "cuisine",
            "temporary-password",
            "new-secure-password"
        );

        assertEquals("jwt-token", response.token());
        assertFalse(response.mustChangePassword());
        assertEquals("encoded-new-password", user.getPasswordHash());
        assertFalse(user.mustChangePassword());
        verify(staffUserRepository).save(user);
    }

    @Test
    void rejectsUnknownUserWithGenericAuthenticationError() {
        when(staffUserRepository.findByUsernameIgnoreCase("unknown")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login("unknown", "password"));
    }

    @Test
    void rejectsWrongPasswordWithGenericAuthenticationError() {
        StaffUser user = new StaffUser("manager", "encoded-password", Role.MANAGER);
        when(staffUserRepository.findByUsernameIgnoreCase("manager")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login("manager", "wrong"));
    }
}