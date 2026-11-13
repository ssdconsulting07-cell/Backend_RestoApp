package sn.chezketchup.backend.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import sn.chezketchup.backend.commandes.domaine.TransitionInterditeException;
import sn.chezketchup.backend.paiements.application.PaymentProvider;
import sn.chezketchup.backend.commun.erreurs.ConflitException;
import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;
import sn.chezketchup.backend.commun.erreurs.RessourceIntrouvableException;
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

    // --- Erreurs metier (lots 2+) ---------------------------------------------

    @ExceptionHandler(ConflitException.class)
    public ResponseEntity<ApiError> handleConflit(ConflitException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<ApiError> handleIntrouvable(RessourceIntrouvableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(RequeteInvalideException.class)
    public ResponseEntity<ApiError> handleRequeteInvalide(RequeteInvalideException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(ex.getCode(), ex.getMessage(), ex.getChamp()));
    }

    /** Webhook de paiement non authentifie : ignore (le prestataire legitime signe toujours). */
    @ExceptionHandler(PaymentProvider.SignatureInvalideException.class)
    public ResponseEntity<ApiError> handleSignatureInvalide(PaymentProvider.SignatureInvalideException ex) {
        LOG.warn("Notification de paiement rejetee : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("SIGNATURE_INVALIDE", ex.getMessage()));
    }

    /** Transition de commande hors de la table pour ce role / ce mode (contrat : 403). */
    @ExceptionHandler(TransitionInterditeException.class)
    public ResponseEntity<ApiError> handleTransitionInterdite(TransitionInterditeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of("TRANSITION_INTERDITE", ex.getMessage()));
    }

    /** Deux modifications simultanees de la meme fiche : la seconde est refusee, jamais ecrasee. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleVerrouOptimiste(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("VERSION_CONFLICT",
                "Cette fiche a ete modifiee entre-temps. Rechargez-la avant d'enregistrer."));
    }

    /** Contrainte de base (unicite...) violee malgre les controles du service (acces concurrent). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrite(DataIntegrityViolationException ex) {
        LOG.warn("Contrainte de base violee : {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("CONFLIT_DONNEES",
                "Operation impossible : elle entre en conflit avec des donnees existantes."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTropVolumineux(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of("PAYLOAD_TOO_LARGE", "Fichier trop volumineux (2 Mo maximum)."));
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
