package sn.chezketchup.backend.commandes.domaine;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import sn.chezketchup.backend.commandes.domaine.Enums.TypeLigne;

/**
 * Ligne de commande : nom et prix FIGES a la creation (copie du catalogue a
 * cet instant). Prix unitaire = prix du produit + prix de l'accompagnement.
 */
@Entity
@Table(name = "lignes_commande")
public class LigneCommande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private TypeLigne type;

    @Column(name = "produit_id", nullable = false)
    private long produitId;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(name = "prix_unitaire", nullable = false)
    private long prixUnitaire;

    @Column(nullable = false)
    private int quantite;

    @Column(name = "accompagnement_id")
    private Long accompagnementId;

    @Column(name = "accompagnement_nom", length = 80)
    private String accompagnementNom;

    @Column(name = "accompagnement_prix")
    private Long accompagnementPrix;

    protected LigneCommande() {
    }

    LigneCommande(Commande commande, int position, LigneTarifee l) {
        this.commande = commande;
        this.position = position;
        this.type = l.type();
        this.produitId = l.produitId();
        this.nom = l.nom();
        this.prixUnitaire = l.prixUnitaire();
        this.quantite = l.quantite();
        this.accompagnementId = l.accompagnementId();
        this.accompagnementNom = l.accompagnementNom();
        this.accompagnementPrix = l.accompagnementPrix();
    }

    /** Ligne verifiee et tarifee par le serveur, prete a etre figee. */
    public record LigneTarifee(TypeLigne type, long produitId, String nom, long prixUnitaire, int quantite,
                               Long accompagnementId, String accompagnementNom, Long accompagnementPrix) {

        public long montant() {
            return prixUnitaire * quantite;
        }
    }

    public int getPosition() {
        return position;
    }

    public TypeLigne getType() {
        return type;
    }

    public long getProduitId() {
        return produitId;
    }

    public String getNom() {
        return nom;
    }

    public long getPrixUnitaire() {
        return prixUnitaire;
    }

    public int getQuantite() {
        return quantite;
    }

    public Long getAccompagnementId() {
        return accompagnementId;
    }

    public String getAccompagnementNom() {
        return accompagnementNom;
    }

    public Long getAccompagnementPrix() {
        return accompagnementPrix;
    }
}
