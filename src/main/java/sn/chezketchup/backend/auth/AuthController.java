package sn.chezketchup.backend.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.chezketchup.backend.web.ApiError;

@RestController
@RequestMapping("/auth")
@Tag(
        name = "Authentification",
        description = "Connexion du staff SenYummies Manager. L'App Client n'appelle jamais ces routes : il reste toujours anonyme."
)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Connexion du staff SenYummies Manager",
            description = "Seul le back-office (SenYummies Manager) authentifie ses utilisateurs. "
                    + "Renvoie un token JWT et le role du staff, utilise par le frontend pour rediriger vers son espace."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Connexion reussie",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Identifiants incorrects",
            content = @Content(schema = @Schema(implementation = ApiError.class))
    )
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

        @PostMapping("/first-login-password")
        @Operation(
                        summary = "Remplacer un mot de passe temporaire",
                        description = "Valide le mot de passe temporaire, enregistre le nouveau, puis ouvre la session staff."
        )
        public LoginResponse changeTemporaryPassword(@Valid @RequestBody FirstLoginPasswordRequest request) {
                return authService.changeTemporaryPassword(
                                request.username(),
                                request.temporaryPassword(),
                                request.newPassword()
                );
        }
}
