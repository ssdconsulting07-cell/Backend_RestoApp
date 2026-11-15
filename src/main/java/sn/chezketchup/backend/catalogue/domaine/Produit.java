package sn.chezketchup.backend.catalogue.domaine;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import sn.chezketchup.backend.commun.publication.ElementPublie;

@Entity
@Table(name = "produits")
public class Produit extends ElementPublie {

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(length = 300)
    private String description;

    /** F CFA, entier strictement positif. */
    @Column(nullable = false)
    private long prix;

    /** Reference par identifiant (pas d'association chargee) : la regle de visibilite se fait en requete. */
    @Column(name = "categorie_id", nullable = false)
    private Long categorieId;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Disponibilite disponibilite = Disponibilite.EN_STOCK;

    /** Valeur effective du badge (manuelle si forcee, sinon automatique). */
    @Column(nullable = false)
    private boolean tendance;

    /** Badge force par le Gerant : null = automatique (les plus commandes). */
    @Column(name = "tendance_manuelle")
    private Boolean tendanceManuelle;

    /** Parmi les plus commandes des 30 derniers jours (calcul periodique). */
    @Column(name = "tendance_auto", nullable = false)
    private boolean tendanceAuto;

    /** Quantite commandee sur les 30 derniers jours : ordre de popularite. */
    @Column(name = "ventes_recentes", nullable = false)
    private int ventesRecentes;

    protected Produit() {
    }

    public Produit(String nom, String description, long prix, Long categorieId, String photoUrl,
                   Disponibilite disponibilite, Boolean tendanceManuelle) {
        modifier(nom, description, prix, categorieId, photoUrl, disponibilite, tendanceManuelle);
    }

    public void modifier(String nom, String description, long prix, Long categorieId, String photoUrl,
                         Disponibilite disponibilite, Boolean tendanceManuelle) {
        this.nom = nom.trim();
        this.description = vide(description);
        this.prix = prix;
        this.categorieId = categorieId;
        this.photoUrl = vide(photoUrl);
        this.disponibilite = disponibilite != null ? disponibilite : Disponibilite.EN_STOCK;
        this.tendanceManuelle = tendanceManuelle;
        recalculerTendance();
    }

    /** Resultat du calcul de popularite. Retourne vrai si le badge affiche change. */
    public boolean appliquerPopularite(int ventes, boolean parmiLesPlusCommandes) {
        boolean avant = tendance;
        boolean change = ventes != ventesRecentes || parmiLesPlusCommandes != tendanceAuto;
        this.ventesRecentes = ventes;
        this.tendanceAuto = parmiLesPlusCommandes;
        recalculerTendance();
        return change || avant != tendance;
    }

    private void recalculerTendance() {
        this.tendance = tendanceManuelle != null ? tendanceManuelle : tendanceAuto;
    }

    /** Reaffectation lors de l'archivage de la categorie d'origine. */
    public void changerCategorie(Long categorieId) {
        this.categorieId = categorieId;
    }

    static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public String getNom() {
        return nom;
    }

    public String getDescription() {
        return description;
    }

    public long getPrix() {
        return prix;
    }

    public Long getCategorieId() {
        return categorieId;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public Disponibilite getDisponibilite() {
        return disponibilite;
    }

    public boolean isTendance() {
        return tendance;
    }

    public Boolean getTendanceManuelle() {
        return tendanceManuelle;
    }

    /** AUTO, OUI ou NON (choix du Gerant). */
    public String getTendanceMode() {
        return tendanceManuelle == null ? "AUTO" : (tendanceManuelle ? "OUI" : "NON");
    }

    public boolean isTendanceAuto() {
        return tendanceAuto;
    }

    public int getVentesRecentes() {
        return ventesRecentes;
    }
}
