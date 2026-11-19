package sn.chezketchup.backend.restaurant;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sn.chezketchup.backend.commandes.domaine.Enums.Canal;
import sn.chezketchup.backend.commandes.domaine.Enums.ModeCommande;
import sn.chezketchup.backend.commandes.domaine.MoyenPaiement;
import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;

/**
 * Ce que le restaurant propose (restaurant.json, genere par l'outil-client) :
 * moyens de paiement et modes de commande. Les apps masquent ce qui n'est pas
 * propose ; le serveur le REFUSE aussi, pour qu'un appel direct a l'API ne
 * contourne pas le choix du restaurant.
 *
 *   APP_RESTAURANT_MOYENS_PAIEMENT=wave,orange-money,mixx-yas,carte,especes (defaut : tous)
 *   APP_RESTAURANT_MODES=livraison,retrait                                 (defaut : les deux)
 *
 * Les modes ne concernent que l'App Client : une vente au comptoir est
 * toujours "a emporter".
 */
@Component
public class OffreRestaurant {

    private final Set<MoyenPaiement> moyens;
    private final Set<ModeCommande> modes;

    public OffreRestaurant(
            @Value("${app.restaurant.moyens-paiement:wave,orange-money,mixx-yas,carte,especes}") String moyens,
            @Value("${app.restaurant.modes:livraison,retrait}") String modes) {
        this.moyens = liste(moyens).stream().map(OffreRestaurant::moyen)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(MoyenPaiement.class)));
        this.modes = liste(modes).stream().map(OffreRestaurant::mode)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ModeCommande.class)));
        if (this.moyens.isEmpty()) {
            throw new IllegalStateException("APP_RESTAURANT_MOYENS_PAIEMENT : au moins un moyen de paiement");
        }
        if (this.modes.isEmpty()) {
            throw new IllegalStateException("APP_RESTAURANT_MODES : au moins un mode (livraison, retrait)");
        }
    }

    public Set<MoyenPaiement> moyens() {
        return Set.copyOf(moyens);
    }

    public Set<ModeCommande> modes() {
        return Set.copyOf(modes);
    }

    /** 400 si le restaurant ne propose pas ce mode (en ligne) ou ce moyen de paiement. */
    public void verifier(Canal canal, ModeCommande mode, MoyenPaiement moyen) {
        if (canal != Canal.COMPTOIR && mode != null && !modes.contains(mode)) {
            throw new RequeteInvalideException("MODE_NON_PROPOSE", mode == ModeCommande.LIVRAISON
                    ? "Le restaurant ne propose pas la livraison." : "Le restaurant ne propose pas le retrait au comptoir.",
                    "mode");
        }
        if (moyen != null && !moyens.contains(moyen)) {
            throw new RequeteInvalideException("MOYEN_PAIEMENT_NON_PROPOSE",
                    "Ce moyen de paiement n'est pas proposé par le restaurant.", "moyenPaiement");
        }
    }

    private static Set<String> liste(String valeur) {
        return Arrays.stream(valeur == null ? new String[0] : valeur.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static MoyenPaiement moyen(String code) {
        try {
            return MoyenPaiement.depuisCode(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_RESTAURANT_MOYENS_PAIEMENT : « " + code
                    + " » inconnu (wave, orange-money, mixx-yas, carte, especes)", e);
        }
    }

    private static ModeCommande mode(String code) {
        try {
            return ModeCommande.valueOf(code.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_RESTAURANT_MODES : « " + code + " » inconnu (livraison, retrait)", e);
        }
    }
}
