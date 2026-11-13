package sn.chezketchup.backend.commandes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.ANNULEE;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.EN_LIVRAISON;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.EN_PREPARATION;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.LIVREE;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.PAYEE;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.PRETE;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.RETIREE;
import static sn.chezketchup.backend.security.Role.CUISINE;
import static sn.chezketchup.backend.security.Role.GERANT;
import static sn.chezketchup.backend.security.Role.LIVREUR;
import static sn.chezketchup.backend.security.Role.VENDEUR;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import sn.chezketchup.backend.commandes.application.JetonSuivi;
import sn.chezketchup.backend.commandes.application.Telephone;
import sn.chezketchup.backend.commandes.domaine.Commande;
import sn.chezketchup.backend.commandes.domaine.Commande.Destinataire;
import sn.chezketchup.backend.commandes.domaine.Enums.Canal;
import sn.chezketchup.backend.commandes.domaine.Enums.ModeCommande;
import sn.chezketchup.backend.commandes.domaine.Enums.TypeLigne;
import sn.chezketchup.backend.commandes.domaine.LigneCommande.LigneTarifee;
import sn.chezketchup.backend.commandes.domaine.MoyenPaiement;
import sn.chezketchup.backend.commandes.domaine.StatutPaiement;
import sn.chezketchup.backend.commandes.domaine.TransitionInterditeException;
import sn.chezketchup.backend.commandes.domaine.TransitionsCommande;
import sn.chezketchup.backend.commun.erreurs.ConflitException;

class ReglesCommandesTest {

    private static final Instant T = Instant.parse("2026-11-13T12:00:00Z");

    private static Commande commande(ModeCommande mode) {
        Destinataire d = new Destinataire("Awa", null, "+221771234567", null, null, null, null, null, null, null);
        List<LigneTarifee> lignes = List.of(new LigneTarifee(TypeLigne.PRODUIT, 1, "Burger", 3500, 2, null, null, null));
        return Commande.nouvelle(Canal.APP, mode, MoyenPaiement.WAVE, d, lignes, mode == ModeCommande.LIVRAISON ? 1000 : 0, null, T);
    }

    @Test
    void tableDesTransitionsIdentiqueAuManager() {
        // Manager_RestoApp src/auth/transitions.js
        assertThat(TransitionsCommande.autorisee(PAYEE, EN_PREPARATION, CUISINE, ModeCommande.LIVRAISON)).isTrue();
        assertThat(TransitionsCommande.autorisee(EN_PREPARATION, PRETE, CUISINE, ModeCommande.RETRAIT)).isTrue();
        assertThat(TransitionsCommande.autorisee(PRETE, EN_LIVRAISON, CUISINE, ModeCommande.LIVRAISON)).isTrue();
        assertThat(TransitionsCommande.autorisee(PRETE, EN_LIVRAISON, LIVREUR, ModeCommande.LIVRAISON)).isTrue();
        assertThat(TransitionsCommande.autorisee(EN_LIVRAISON, LIVREE, LIVREUR, ModeCommande.LIVRAISON)).isTrue();
        assertThat(TransitionsCommande.autorisee(PRETE, RETIREE, VENDEUR, ModeCommande.RETRAIT)).isTrue();
        assertThat(TransitionsCommande.autorisee(PAYEE, ANNULEE, CUISINE, ModeCommande.RETRAIT)).isTrue();

        assertThat(TransitionsCommande.autorisee(PRETE, EN_LIVRAISON, LIVREUR, ModeCommande.RETRAIT)).as("mode").isFalse();
        assertThat(TransitionsCommande.autorisee(PRETE, RETIREE, VENDEUR, ModeCommande.LIVRAISON)).as("mode").isFalse();
        assertThat(TransitionsCommande.autorisee(EN_LIVRAISON, LIVREE, CUISINE, ModeCommande.LIVRAISON)).as("role").isFalse();
        assertThat(TransitionsCommande.autorisee(PAYEE, EN_PREPARATION, GERANT, ModeCommande.LIVRAISON)).as("role").isFalse();
        assertThat(TransitionsCommande.autorisee(EN_PREPARATION, ANNULEE, CUISINE, ModeCommande.LIVRAISON))
                .as("annulation seulement avant la preparation").isFalse();
        assertThat(TransitionsCommande.TABLE).hasSize(6);
    }

    @Test
    void lesMontantsSontCalculesParLeServeur() {
        Commande c = commande(ModeCommande.LIVRAISON);
        assertThat(c.getSousTotal()).isEqualTo(7000);
        assertThat(c.getTotal()).isEqualTo(8000);
        assertThat(c.getStatut().name()).isEqualTo("CREEE");
        assertThat(c.getStatutPaiement()).isEqualTo(StatutPaiement.EN_ATTENTE);
    }

    @Test
    void confirmationDePaiementIdempotente() {
        Commande c = commande(ModeCommande.RETRAIT);
        c.confirmerPaiement("systeme", T);
        c.confirmerPaiement("systeme", T);
        assertThat(c.getStatut()).isEqualTo(PAYEE);
        assertThat(c.getHistorique()).extracting(h -> h.getStatut().name()).containsExactly("CREEE", "PAYEE");
    }

    @Test
    void paiementEchoueAnnuleLaCommande() {
        Commande c = commande(ModeCommande.RETRAIT);
        c.echecPaiement("systeme", T);
        assertThat(c.getStatut()).isEqualTo(ANNULEE);
        assertThat(c.getStatutPaiement()).isEqualTo(StatutPaiement.ECHOUE);
        assertThat(c.isRemboursementASignaler()).isFalse();
    }

    @Test
    void commandePayeePuisAnnuleeRemboursementASignaler() {
        Commande c = commande(ModeCommande.LIVRAISON);
        c.confirmerPaiement("systeme", T);
        c.appliquerTransition(ANNULEE, CUISINE, "moussa", T);
        assertThat(c.isRemboursementASignaler()).isTrue();
    }

    @Test
    void transitionDejaAppliqueeOuInterdite() {
        Commande c = commande(ModeCommande.LIVRAISON);
        c.confirmerPaiement("systeme", T);
        c.appliquerTransition(EN_PREPARATION, CUISINE, "moussa", T);
        assertThatThrownBy(() -> c.appliquerTransition(EN_PREPARATION, CUISINE, "aminata", T))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode()).isEqualTo("STATUT_DEJA_APPLIQUE");
        assertThatThrownBy(() -> c.appliquerTransition(LIVREE, LIVREUR, "ibrahima", T))
                .isInstanceOf(TransitionInterditeException.class);
        assertThat(c.getHistorique().get(c.getHistorique().size() - 1).getActeur()).isEqualTo("moussa");
    }

    @Test
    void monnaieCalculeeParLeServeur() {
        Commande c = commande(ModeCommande.RETRAIT);
        c.encaisserEspeces(10_000);
        assertThat(c.getMonnaieRendue()).isEqualTo(3_000);
    }

    @Test
    void numerosDeTelephone() {
        assertThat(Telephone.normaliser("77 123 45 67")).hasValue("+221771234567");
        assertThat(Telephone.normaliser("+221771234567")).hasValue("+221771234567");
        assertThat(Telephone.normaliser("00221 77 123 45 67")).hasValue("+221771234567");
        assertThat(Telephone.normaliser("77 12")).isEmpty();
        assertThat(Telephone.normaliser(null)).isEmpty();
    }

    @Test
    void jetonDeSuivi() {
        String jeton = JetonSuivi.generer();
        String empreinte = JetonSuivi.empreinte(jeton);
        assertThat(jeton).hasSizeGreaterThanOrEqualTo(43);
        assertThat(empreinte).hasSize(64).doesNotContain(jeton);
        assertThat(JetonSuivi.correspond(jeton, empreinte)).isTrue();
        assertThat(JetonSuivi.correspond(JetonSuivi.generer(), empreinte)).isFalse();
        assertThat(JetonSuivi.correspond(null, empreinte)).isFalse();
        assertThat(JetonSuivi.generer()).isNotEqualTo(jeton);
    }
}
