package sn.chezketchup.backend.web;

/**
 * Format d'erreur standard renvoye par l'API (convention partagee entre les
 * 3 equipes) : un code stable pour le frontend, un message lisible, et le
 * champ concerne le cas echeant.
 */
public record ApiError(
        String code,
        String message,
        String field
) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError of(String code, String message, String field) {
        return new ApiError(code, message, field);
    }
}
