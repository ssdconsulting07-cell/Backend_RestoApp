package sn.chezketchup.backend.commun.validation;

import java.net.URI;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UrlImageValidator implements ConstraintValidator<UrlImage, String> {

    static final int LONGUEUR_MAX = 500;
    private static final Pattern PHOTO_LOCALE =
            Pattern.compile("^/api/v1/photos/[a-f0-9-]{36}\\.(jpg|png)$");

    @Override
    public boolean isValid(String valeur, ConstraintValidatorContext contexte) {
        return estValide(valeur);
    }

    public static boolean estValide(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return true;
        }
        if (valeur.length() > LONGUEUR_MAX) {
            return false;
        }
        if (PHOTO_LOCALE.matcher(valeur).matches()) {
            return true;
        }
        try {
            URI uri = URI.create(valeur);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
