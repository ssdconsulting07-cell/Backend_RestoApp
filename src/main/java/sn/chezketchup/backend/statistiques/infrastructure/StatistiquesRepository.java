package sn.chezketchup.backend.statistiques.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Agregats en SQL (lecture seule, sans charger les commandes en memoire).
 *
 * "Encaisse" = paiement confirme ET commande non annulee (meme regle que le
 * Manager : une commande payee puis annulee est un remboursement, pas du
 * chiffre d'affaires).
 *
 * Jours : DATE(cree_le). Les dates sont stockees en UTC et Dakar est en UTC+0
 * toute l'annee (pas d'heure d'ete) : le jour UTC est le jour de Dakar.
 */
@Repository
public class StatistiquesRepository {

    static final String ENCAISSEE = "c.statut_paiement = 'paye' AND c.statut <> 'ANNULEE'";

    private final JdbcClient jdbc;

    public StatistiquesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record LigneCa(LocalDate date, String canal, long commandes, long montant) {
    }

    public record LigneStatut(String statut, long commandes) {
    }

    public record LigneProduit(String type, long produitId, String nom, long quantite, long montant) {
    }

    public record LigneVente(LocalDate date, String vendeur, String moyenPaiement, long ventes, long montant) {
    }

    public List<LigneCa> caParJourEtCanal(Instant du, Instant au) {
        return jdbc.sql("SELECT DATE(c.cree_le) AS jour, c.canal, COUNT(*) AS nb, SUM(c.total) AS montant "
                        + "FROM commandes c WHERE " + ENCAISSEE + " AND c.cree_le >= :du AND c.cree_le < :au "
                        + "GROUP BY DATE(c.cree_le), c.canal ORDER BY jour, c.canal")
                .param("du", Timestamp.from(du)).param("au", Timestamp.from(au))
                .query((rs, i) -> new LigneCa(rs.getDate("jour").toLocalDate(), rs.getString("canal"),
                        rs.getLong("nb"), rs.getLong("montant")))
                .list();
    }

    /** Commandes creees sur la periode, par statut (hors CREEE : pas encore payees). */
    public List<LigneStatut> commandesParStatut(Instant du, Instant au) {
        return jdbc.sql("SELECT c.statut, COUNT(*) AS nb FROM commandes c "
                        + "WHERE c.statut <> 'CREEE' AND c.cree_le >= :du AND c.cree_le < :au GROUP BY c.statut")
                .param("du", Timestamp.from(du)).param("au", Timestamp.from(au))
                .query((rs, i) -> new LigneStatut(rs.getString("statut"), rs.getLong("nb")))
                .list();
    }

    public List<LigneProduit> topProduits(Instant du, Instant au, int limite) {
        return jdbc.sql("SELECT l.type, l.produit_id, MAX(l.nom) AS nom, SUM(l.quantite) AS quantite, "
                        + "SUM(l.prix_unitaire * l.quantite) AS montant "
                        + "FROM lignes_commande l JOIN commandes c ON c.id = l.commande_id "
                        + "WHERE " + ENCAISSEE + " AND c.cree_le >= :du AND c.cree_le < :au "
                        + "GROUP BY l.type, l.produit_id ORDER BY quantite DESC, montant DESC LIMIT :limite")
                .param("du", Timestamp.from(du)).param("au", Timestamp.from(au)).param("limite", limite)
                .query((rs, i) -> new LigneProduit(rs.getString("type"), rs.getLong("produit_id"), rs.getString("nom"),
                        rs.getLong("quantite"), rs.getLong("montant")))
                .list();
    }

    /** Ventes au comptoir par jour, vendeur et moyen de paiement (vendeur null = tous). */
    public List<LigneVente> ventesComptoir(Instant du, Instant au, String vendeur) {
        return jdbc.sql("SELECT DATE(c.cree_le) AS jour, c.cree_par, c.moyen_paiement, COUNT(*) AS nb, SUM(c.total) AS montant "
                        + "FROM commandes c WHERE c.canal = 'COMPTOIR' AND " + ENCAISSEE
                        + " AND c.cree_le >= :du AND c.cree_le < :au AND (:vendeur IS NULL OR c.cree_par = :vendeur) "
                        + "GROUP BY DATE(c.cree_le), c.cree_par, c.moyen_paiement ORDER BY jour DESC, c.cree_par")
                .param("du", Timestamp.from(du)).param("au", Timestamp.from(au)).param("vendeur", vendeur)
                .query((rs, i) -> new LigneVente(rs.getDate("jour").toLocalDate(), rs.getString("cree_par"),
                        rs.getString("moyen_paiement"), rs.getLong("nb"), rs.getLong("montant")))
                .list();
    }
}
