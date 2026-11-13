package sn.chezketchup.backend.commandes.domaine;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Statut de paiement, en MINUSCULES dans l'API comme dans le contrat
 * (en_attente | paye | echoue). Corrige le decalage releve avec l'ancien
 * enum PaymentStatus en majuscules.
 */
public enum StatutPaiement {
    EN_ATTENTE("en_attente"),
    PAYE("paye"),
    ECHOUE("echoue");

    private final String code;

    StatutPaiement(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static StatutPaiement depuisCode(String code) {
        return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Statut de paiement inconnu : " + code));
    }

    @Converter(autoApply = true)
    public static class ConvertisseurBase implements AttributeConverter<StatutPaiement, String> {

        @Override
        public String convertToDatabaseColumn(StatutPaiement s) {
            return s == null ? null : s.code;
        }

        @Override
        public StatutPaiement convertToEntityAttribute(String code) {
            return code == null ? null : depuisCode(code);
        }
    }
}
