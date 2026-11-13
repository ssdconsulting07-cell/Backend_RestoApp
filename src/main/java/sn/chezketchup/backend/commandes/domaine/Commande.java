package sn.chezketchup.backend.commandes.domaine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sn.chezketchup.backend.commandes.domaine.Enums.Canal;
import sn.chezketchup.backend.commandes.domaine.Enums.ModeCommande;
import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;
import sn.chezketchup.backend.commandes.domaine.LigneCommande.LigneTarifee;
import sn.chezketchup.backend.commun.erreurs.ConflitException;
import sn.chezketchup.backend.security.Role;

/**
 * Commande (App Client ou comptoir). L'identifiant est le numero de ticket.
 * Les montants sont calcules par le serveur et figes ; le statut n'evolue
 * que par les transitions autorisees (TransitionsCommande).
 */
@Entity
@Table(name = "commandes")
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Canal canal;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private ModeCommande mode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private StatutCommande statut;

    @Column(name = "moyen_paiement", nullable = false, length = 15)
    private MoyenPaiement moyenPaiement;

    @Column(name = "statut_paiement", nullable = false, length = 12)
    private StatutPaiement statutPaiement;

    @Column(name = "client_prenom", length = 60)
    private String clientPrenom;

    @Column(name = "client_nom", length = 60)
    private String clientNom;

    @Column(name = "client_telephone", length = 16)
    private String clientTelephone;

    @Column(name = "secteur_id", length = 80)
    private String secteurId;

    @Column(name = "adresse_livraison", length = 80)
    private String adresseLivraison;

    @Column(name = "region_livraison", length = 20)
    private String regionLivraison;

    @Column(name = "position_latitude")
    private Double positionLatitude;

    @Column(name = "position_longitude")
    private Double positionLongitude;

    @Column(name = "position_precision")
    private Integer positionPrecision;

    @Column(length = 300)
    private String note;

    @Column(name = "sous_total", nullable = false)
    private long sousTotal;

    @Column(name = "frais_livraison", nullable = false)
    private long fraisLivraison;

    @Column(nullable = false)
    private long total;

    @Column(name = "montant_recu")
    private Long montantRecu;

    @Column(name = "monnaie_rendue")
    private Long monnaieRendue;

    @Column(name = "jeton_suivi_empreinte", length = 64)
    private String jetonSuiviEmpreinte;

    @Column(name = "remboursement_a_signaler", nullable = false)
    private boolean remboursementASignaler;

    @Column(name = "cree_par")
    private String creePar;

    @Version
    private long version;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<LigneCommande> lignes = new ArrayList<>();

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("horodatage")
    private List<ChangementStatut> historique = new ArrayList<>();

    protected Commande() {
    }

    /** Donnees client et livraison deja validees par le service de creation. */
    public record Destinataire(String prenom, String nom, String telephone, String secteurId, String adresseLivraison,
                               String regionLivraison, Double latitude, Double longitude, Integer precision, String note) {
    }

    public static Commande nouvelle(Canal canal, ModeCommande mode, MoyenPaiement moyenPaiement, Destinataire d,
                                    List<LigneTarifee> lignes, long fraisLivraison, String creePar, Instant maintenant) {
        Commande c = new Commande();
        c.canal = canal;
        c.mode = mode;
        c.moyenPaiement = moyenPaiement;
        c.statut = StatutCommande.CREEE;
        c.statutPaiement = StatutPaiement.EN_ATTENTE;
        c.clientPrenom = d.prenom();
        c.clientNom = d.nom();
        c.clientTelephone = d.telephone();
        c.secteurId = d.secteurId();
        c.adresseLivraison = d.adresseLivraison();
        c.regionLivraison = d.regionLivraison();
        c.positionLatitude = d.latitude();
        c.positionLongitude = d.longitude();
        c.positionPrecision = d.precision();
        c.note = d.note();
        for (int i = 0; i < lignes.size(); i++) {
            c.lignes.add(new LigneCommande(c, i, lignes.get(i)));
        }
        c.sousTotal = lignes.stream().mapToLong(LigneTarifee::montant).sum();
        c.fraisLivraison = fraisLivraison;
        c.total = c.sousTotal + fraisLivraison;
        c.creePar = creePar;
        c.creeLe = maintenant;
        c.historique.add(new ChangementStatut(c, StatutCommande.CREEE, maintenant,
                creePar != null ? creePar : "client", null));
        return c;
    }

    @PrePersist
    @PreUpdate
    void horodater() {
        modifieLe = Instant.now();
    }

    /** Paiement confirme (par le serveur : prestataire ou especes) : la commande part en cuisine. */
    public void confirmerPaiement(String acteur, Instant maintenant) {
        if (statut != StatutCommande.CREEE) {
            return; // confirmation rejouee : sans effet (idempotence)
        }
        statutPaiement = StatutPaiement.PAYE;
        changerStatut(StatutCommande.PAYEE, acteur, null, maintenant);
    }

    /** Paiement refuse ou abandonne : la commande est annulee, rien n'est prepare. */
    public void echecPaiement(String acteur, Instant maintenant) {
        if (statut != StatutCommande.CREEE) {
            return;
        }
        statutPaiement = StatutPaiement.ECHOUE;
        changerStatut(StatutCommande.ANNULEE, acteur, null, maintenant);
    }

    /** Vente comptoir en especes : la monnaie est calculee ici, jamais recue du client. */
    public void encaisserEspeces(long montantRecu) {
        this.montantRecu = montantRecu;
        this.monnaieRendue = montantRecu - total;
    }

    /**
     * Transition demandee par le staff. Verrou pris par l'appelant (SELECT ...
     * FOR UPDATE) : deux postes qui passent la meme commande en meme temps
     * sont servis l'un apres l'autre, et le second voit le nouveau statut.
     */
    public void appliquerTransition(StatutCommande cible, Role role, String acteur, Instant maintenant) {
        if (statut == cible) {
            throw new ConflitException("STATUT_DEJA_APPLIQUE",
                    "Cette commande est deja « " + cible + " » (un autre poste vient de la traiter).");
        }
        if (!TransitionsCommande.autorisee(statut, cible, role, mode)) {
            throw new TransitionInterditeException(statut, cible, role);
        }
        if (cible == StatutCommande.ANNULEE && statutPaiement == StatutPaiement.PAYE) {
            // Compensation : l'argent a ete encaisse, il faudra rembourser.
            remboursementASignaler = true;
        }
        changerStatut(cible, acteur, role.name(), maintenant);
    }

    private void changerStatut(StatutCommande cible, String acteur, String role, Instant maintenant) {
        statut = cible;
        historique.add(new ChangementStatut(this, cible, maintenant, acteur, role));
    }

    public void attacherJetonSuivi(String empreinte) {
        this.jetonSuiviEmpreinte = empreinte;
    }

    public Long getId() {
        return id;
    }

    public Canal getCanal() {
        return canal;
    }

    public ModeCommande getMode() {
        return mode;
    }

    public StatutCommande getStatut() {
        return statut;
    }

    public MoyenPaiement getMoyenPaiement() {
        return moyenPaiement;
    }

    public StatutPaiement getStatutPaiement() {
        return statutPaiement;
    }

    public String getClientPrenom() {
        return clientPrenom;
    }

    public String getClientNom() {
        return clientNom;
    }

    public String getClientTelephone() {
        return clientTelephone;
    }

    public String getSecteurId() {
        return secteurId;
    }

    public String getAdresseLivraison() {
        return adresseLivraison;
    }

    public String getRegionLivraison() {
        return regionLivraison;
    }

    public Double getPositionLatitude() {
        return positionLatitude;
    }

    public Double getPositionLongitude() {
        return positionLongitude;
    }

    public Integer getPositionPrecision() {
        return positionPrecision;
    }

    public String getNote() {
        return note;
    }

    public long getSousTotal() {
        return sousTotal;
    }

    public long getFraisLivraison() {
        return fraisLivraison;
    }

    public long getTotal() {
        return total;
    }

    public Long getMontantRecu() {
        return montantRecu;
    }

    public Long getMonnaieRendue() {
        return monnaieRendue;
    }

    public String getJetonSuiviEmpreinte() {
        return jetonSuiviEmpreinte;
    }

    public boolean isRemboursementASignaler() {
        return remboursementASignaler;
    }

    public String getCreePar() {
        return creePar;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public List<LigneCommande> getLignes() {
        return lignes;
    }

    public List<ChangementStatut> getHistorique() {
        return historique;
    }
}
