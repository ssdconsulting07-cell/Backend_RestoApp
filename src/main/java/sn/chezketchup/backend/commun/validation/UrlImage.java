package sn.chezketchup.backend.commun.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Adresse d'image acceptee : une photo deposee chez nous
 * (/api/v1/photos/...) ou une adresse https. Refuse javascript:, data:,
 * http: en clair, etc. Vide ou null = pas d'image.
 */
@Documented
@Constraint(validatedBy = UrlImageValidator.class)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface UrlImage {

    String message() default "Adresse d'image invalide (photo deposee ou adresse https).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
