package sn.chezketchup.backend.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirstLoginPasswordRequest(
        @NotBlank String username,
        @NotBlank String temporaryPassword,
        @NotBlank
        @Size(min = 12, max = 128)
        @Schema(description = "Nouveau mot de passe choisi par le membre du personnel, 12 caracteres minimum")
        String newPassword
) {
}