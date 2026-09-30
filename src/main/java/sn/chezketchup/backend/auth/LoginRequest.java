package sn.chezketchup.backend.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "Identifiant du staff (ex : cuisine, gerant, manager, livreur)", example = "cuisine")
        @NotBlank String username,

        @Schema(description = "Mot de passe du staff")
        @NotBlank String password
) {
}
