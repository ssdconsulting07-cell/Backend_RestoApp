package sn.chezketchup.backend.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Format d'erreur standard renvoye par l'API (convention partagee entre les
 * 3 equipes) : un code stable pour le frontend, un message lisible, et le
 * champ concerne le cas echeant.
 */
@Schema(description = "Format d'erreur standard, commun aux 3 equipes (App Client, SenYummies Manager, Backend).")
public record ApiError(
        @Schema(description = "Code d'erreur stable, a utiliser par le frontend plutot que le message "
                + "(ex : INVALID_CREDENTIALS, UNAUTHENTICATED, ACCESS_DENIED, VALIDATION_ERROR)")
        String code,

        @Schema(description = "Message lisible, affichable tel quel si besoin")
        String message,

        @Schema(description = "Champ concerne par l'erreur, si applicable (ex : validation d'un formulaire)", nullable = true)
        String field
) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError of(String code, String message, String field) {
        return new ApiError(code, message, field);
    }
}
