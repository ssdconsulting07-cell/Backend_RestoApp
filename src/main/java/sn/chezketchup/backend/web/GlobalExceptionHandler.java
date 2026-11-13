package sn.chezketchup.backend.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.FieldError;

/**
 * Traduit toute exception non geree en {@link ApiError}, pour que App Client
 * et SenYummies Manager traitent les erreurs API de facon identique.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String field = fieldError != null ? fieldError.getField() : null;
        String message = fieldError != null ? fieldError.getDefaultMessage() : "Requete invalide";
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR", message, field));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("INVALID_CREDENTIALS", "Identifiants incorrects"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("ACCESS_DENIED", "Acces refuse pour ce role"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiError.of("REQUEST_ERROR", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }

    /**
     * Erreurs du framework qui portent deja leur propre statut HTTP (route
     * inconnue 404, methode non supportee 405, type de contenu 415, parametre
     * manquant 400...). Sans ce handler, le handler generique les transformait
     * toutes en 500.
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiError> handleFramework(ErrorResponseException ex) {
        return reponsePourStatut(ex.getStatusCode());
    }

    @ExceptionHandler({
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleRequeteMvc(Exception ex) {
        HttpStatusCode statut = ex instanceof ErrorResponse er ? er.getStatusCode() : HttpStatus.BAD_REQUEST;
        return reponsePourStatut(statut);
    }

    /** Corps JSON illisible ou mal forme : 400, pas 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleCorpsIllisible(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_BODY", "Corps de requete illisible ou mal forme"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        // Trace complete cote serveur (sinon une vraie panne est indiagnosticable) ;
        // rien de technique dans la reponse.
        LOG.error("Erreur inattendue", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "Une erreur inattendue est survenue"));
    }

    static ResponseEntity<ApiError> reponsePourStatut(HttpStatusCode statut) {
        String code = switch (statut.value()) {
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> statut.is4xxClientError() ? "REQUEST_ERROR" : "INTERNAL_ERROR";
        };
        String message = switch (statut.value()) {
            case 404 -> "Ressource introuvable";
            case 405 -> "Methode HTTP non autorisee sur cette ressource";
            case 413 -> "Contenu trop volumineux";
            case 415 -> "Type de contenu non pris en charge";
            default -> statut.is4xxClientError() ? "Requete invalide" : "Une erreur inattendue est survenue";
        };
        return ResponseEntity.status(statut).body(ApiError.of(code, message));
    }
}
