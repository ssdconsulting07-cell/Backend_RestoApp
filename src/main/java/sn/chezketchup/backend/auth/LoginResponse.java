package sn.chezketchup.backend.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import sn.chezketchup.backend.security.Role;

@Schema(description = "Reponse de connexion : token JWT a renvoyer ensuite dans le header Authorization, "
        + "et role du staff utilise par le frontend pour choisir vers quel espace rediriger.")
public record LoginResponse(
        @Schema(description = "Token JWT, a envoyer ensuite en 'Authorization: Bearer <token>'") String token,
        @Schema(description = "Role du staff connecte (CUISINE, GERANT, MANAGER, LIVREUR)") Role role,
        @Schema(description = "Vrai si le mot de passe temporaire doit etre remplace avant l'acces au Manager")
        boolean mustChangePassword
) {
}
