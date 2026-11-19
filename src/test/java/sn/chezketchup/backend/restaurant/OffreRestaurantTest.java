package sn.chezketchup.backend.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import sn.chezketchup.backend.commandes.domaine.Enums.Canal;
import sn.chezketchup.backend.commandes.domaine.Enums.ModeCommande;
import sn.chezketchup.backend.commandes.domaine.MoyenPaiement;
import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;
import sn.chezketchup.backend.commun.publication.StatutPublication;

class OffreRestaurantTest {

    private static final Canal EN_LIGNE = Canal.APP;

    @Test
    void parDefautToutEstPropose() {
        OffreRestaurant offre = new OffreRestaurant("wave,orange-money,mixx-yas,carte,especes", "livraison,retrait");
        assertThat(offre.moyens()).containsExactlyInAnyOrder(MoyenPaiement.values());
        assertThat(offre.modes()).containsExactlyInAnyOrder(ModeCommande.values());
        assertThatCode(() -> offre.verifier(EN_LIGNE, ModeCommande.LIVRAISON, MoyenPaiement.MIXX_YAS))
                .doesNotThrowAnyException();
    }

    @Test
    void unMoyenNonProposeEstRefuseMemeViaLApi() {
        OffreRestaurant offre = new OffreRestaurant(" Wave , carte ", "livraison,retrait");
        assertThatCode(() -> offre.verifier(EN_LIGNE, ModeCommande.RETRAIT, MoyenPaiement.WAVE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> offre.verifier(EN_LIGNE, ModeCommande.RETRAIT, MoyenPaiement.ORANGE_MONEY))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("n'est pas proposé");
        assertThatThrownBy(() -> offre.verifier(Canal.COMPTOIR, ModeCommande.RETRAIT, MoyenPaiement.ESPECES))
                .isInstanceOf(RequeteInvalideException.class);
    }

    @Test
    void unModeNonProposeEstRefuseEnLigneMaisPasAuComptoir() {
        OffreRestaurant offre = new OffreRestaurant("carte,especes", "livraison");
        assertThatThrownBy(() -> offre.verifier(EN_LIGNE, ModeCommande.RETRAIT, MoyenPaiement.CARTE))
                .isInstanceOf(RequeteInvalideException.class)
                .hasMessageContaining("retrait");
        // La vente au comptoir est toujours a emporter.
        assertThatCode(() -> offre.verifier(Canal.COMPTOIR, ModeCommande.RETRAIT, MoyenPaiement.ESPECES))
                .doesNotThrowAnyException();
    }

    @Test
    void uneConfigurationInvalideEmpecheLeDemarrage() {
        assertThatThrownBy(() -> new OffreRestaurant("wave,cheque", "livraison"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cheque");
        assertThatThrownBy(() -> new OffreRestaurant("", "livraison"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OffreRestaurant("wave", "emporter"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("emporter");
    }

    @Test
    void cheminDeStatutPasseParBrouillonSiNecessaire() {
        assertThat(ImportRestaurant.chemin(StatutPublication.BROUILLON, StatutPublication.ACTIF))
                .containsExactly(StatutPublication.ACTIF);
        assertThat(ImportRestaurant.chemin(StatutPublication.ARCHIVE, StatutPublication.ACTIF))
                .containsExactly(StatutPublication.BROUILLON, StatutPublication.ACTIF);
        assertThat(ImportRestaurant.chemin(StatutPublication.ACTIF, StatutPublication.ACTIF)).isEqualTo(List.of());
    }
}
