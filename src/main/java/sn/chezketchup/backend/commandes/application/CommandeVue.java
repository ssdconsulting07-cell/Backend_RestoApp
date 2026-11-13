package sn.chezketchup.backend.commandes.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import sn.chezketchup.backend.commandes.domaine.ChangementStatut;
import sn.chezketchup.backend.commandes.domaine.Commande;
import sn.chezketchup.backend.commandes.domaine.Enums.Canal;
import sn.chezketchup.backend.commandes.domaine.Enums.ModeCommande;
import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;
import sn.chezketchup.backend.commandes.domaine.Enums.TypeLigne;
import sn.chezketchup.backend.commandes.domaine.LigneCommande;
import sn.chezketchup.backend.commandes.domaine.MoyenPaiement;
import sn.chezketchup.backend.commandes.domaine.StatutPaiement;
import sn.chezketchup.backend.security.Role;

/**
 * Representation API d'une commande (contrat : Commande). Les donnees
 * personnelles ne sont renvoyees qu'a qui en a besoin :
 *
 * | Donnee              | Client (jeton) | Client (telephone seul) | Cuisine | Livreur | Vendeur | Manager / Gerant |
 * |---------------------|----------------|-------------------------|---------|---------|---------|------------------|
 * | prenom, nom         | oui            | non                     | oui     | oui     | oui     | oui              |
 * | telephone           | oui            | oui (celui fourni)      | oui     | oui     | oui     | non              |
 * | position GPS        | oui            | non                     | non     | oui     | non     | non              |
 * | note                | oui            | non                     | oui     | oui     | oui     | oui              |
 * | auteurs historique  | non            | non                     | oui     | oui     | oui     | oui              |
 *
 * Champs nuls non serialises : une donnee masquee est simplement absente.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandeVue(
        String id,
        StatutCommande statut,
        Canal canal,
        ModeCommande mode,
        MoyenPaiement moyenPaiement,
        StatutPaiement statutPaiement,
        String clientPrenom,
        String clientNom,
        String clientTelephone,
        String adresseLivraison,
        String regionLivraison,
        String secteurId,
        Position positionLivraison,
        String note,
        List<Ligne> lignes,
        long sousTotal,
        long fraisLivraison,
        long total,
        Long montantRecu,
        Long monnaieRendue,
        Boolean remboursementASignaler,
        List<Etape> historique,
        Long version,
        Instant creeLe) {

    public record Position(double latitude, double longitude, Integer precision) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ligne(String produitId, TypeLigne type, String nom, long prixUnitaire, int quantite,
                        String accompagnementId, String accompagnementNom, Long accompagnementPrix) {

        static Ligne de(LigneCommande l) {
            return new Ligne(String.valueOf(l.getProduitId()), l.getType(), l.getNom(), l.getPrixUnitaire(),
                    l.getQuantite(), l.getAccompagnementId() == null ? null : String.valueOf(l.getAccompagnementId()),
                    l.getAccompagnementNom(), l.getAccompagnementPrix());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Etape(StatutCommande statut, Instant le, String par, String role) {
    }

    private static final Set<Role> VOIENT_TELEPHONE = Set.of(Role.CUISINE, Role.LIVREUR, Role.VENDEUR);

    /** Vue du staff, filtree selon le role. */
    public static CommandeVue pourStaff(Commande c, Role role) {
        boolean telephone = VOIENT_TELEPHONE.contains(role);
        boolean position = role == Role.LIVREUR;
        return construire(c, true, telephone, position, true, true);
    }

    /** Vue complete du client qui detient le jeton de suivi de SA commande. */
    public static CommandeVue pourClient(Commande c) {
        return construire(c, true, true, true, true, false);
    }

    /**
     * Vue reduite par numero de telephone seul (un numero n'est pas un
     * secret) : ni nom, ni position, ni note. Le numero lui-meme est renvoye :
     * le demandeur vient de le fournir, il n'apprend donc rien (et l'App
     * Client actuelle filtre la liste sur ce champ).
     */
    public static CommandeVue reduite(Commande c) {
        return construire(c, false, true, false, false, false);
    }

    private static CommandeVue construire(Commande c, boolean identite, boolean telephone, boolean position,
                                          boolean note, boolean auteurs) {
        Position pos = position && c.getPositionLatitude() != null && c.getPositionLongitude() != null
                ? new Position(c.getPositionLatitude(), c.getPositionLongitude(), c.getPositionPrecision()) : null;
        List<Etape> historique = c.getHistorique().stream().map((ChangementStatut h) -> new Etape(h.getStatut(),
                h.getHorodatage(), auteurs ? h.getActeur() : null, auteurs ? h.getRole() : null)).toList();
        return new CommandeVue(
                String.valueOf(c.getId()), c.getStatut(), c.getCanal(), c.getMode(), c.getMoyenPaiement(),
                c.getStatutPaiement(),
                identite ? c.getClientPrenom() : null,
                identite ? c.getClientNom() : null,
                telephone ? c.getClientTelephone() : null,
                c.getAdresseLivraison(), c.getRegionLivraison(), c.getSecteurId(),
                pos,
                note ? c.getNote() : null,
                c.getLignes().stream().map(Ligne::de).toList(),
                c.getSousTotal(), c.getFraisLivraison(), c.getTotal(),
                c.getMontantRecu(), c.getMonnaieRendue(),
                auteurs && c.isRemboursementASignaler() ? Boolean.TRUE : null,
                historique,
                auteurs ? c.getVersion() : null,
                c.getCreeLe());
    }
}
