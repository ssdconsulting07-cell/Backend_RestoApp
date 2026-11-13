package sn.chezketchup.backend.commun.erreurs;

/** HTTP 404 {code: NOT_FOUND}. */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
