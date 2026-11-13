package sn.chezketchup.backend.commun.erreurs;

/**
 * Donnee refusee par une regle metier : HTTP 400 {code, message, field},
 * le champ permettant au front de cibler la saisie concernee.
 */
public class RequeteInvalideException extends RuntimeException {

    private final String code;
    private final String champ;

    public RequeteInvalideException(String code, String message, String champ) {
        super(message);
        this.code = code;
        this.champ = champ;
    }

    public String getCode() {
        return code;
    }

    public String getChamp() {
        return champ;
    }
}
