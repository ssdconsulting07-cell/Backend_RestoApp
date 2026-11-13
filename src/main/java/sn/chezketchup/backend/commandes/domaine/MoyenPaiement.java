package sn.chezketchup.backend.commandes.domaine;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Codes du contrat (MoyenPaiement) : wave, orange-money, mixx-yas, carte, especes. */
public enum MoyenPaiement {
    WAVE("wave"),
    ORANGE_MONEY("orange-money"),
    MIXX_YAS("mixx-yas"),
    CARTE("carte"),
    ESPECES("especes");

    private final String code;

    MoyenPaiement(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static MoyenPaiement depuisCode(String code) {
        return Arrays.stream(values()).filter(m -> m.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Moyen de paiement inconnu : " + code));
    }

    /** Stocke le code du contrat tel quel en base. */
    @Converter(autoApply = true)
    public static class ConvertisseurBase implements AttributeConverter<MoyenPaiement, String> {

        @Override
        public String convertToDatabaseColumn(MoyenPaiement m) {
            return m == null ? null : m.code;
        }

        @Override
        public MoyenPaiement convertToEntityAttribute(String code) {
            return code == null ? null : depuisCode(code);
        }
    }
}
