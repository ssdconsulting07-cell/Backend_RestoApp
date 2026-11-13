package sn.chezketchup.backend.commun.erreurs;

/**
 * Regle metier ou etat incompatible avec la demande : HTTP 409
 * {code, message}. Ex. VERSION_CONFLICT, TRANSITION_INTERDITE,
 * CATEGORIE_NON_VIDE, SECTEUR_OFFICIEL.
 */
public class ConflitException extends RuntimeException {

    private final String code;

    public ConflitException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
