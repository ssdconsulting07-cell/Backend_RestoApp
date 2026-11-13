package sn.chezketchup.backend.commun.publication;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import sn.chezketchup.backend.commun.erreurs.ConflitException;

/**
 * Base des fiches editoriales (categories, produits, plats du jour,
 * accompagnements, annonces) : identifiant, statut de publication, version
 * (verrouillage optimiste) et horodatage.
 */
@MappedSuperclass
public abstract class ElementPublie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 12)
    private StatutPublication statut = StatutPublication.BROUILLON;

    @Version
    private long version;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @PrePersist
    void avantCreation() {
        Instant maintenant = Instant.now();
        creeLe = maintenant;
        modifieLe = maintenant;
    }

    @PreUpdate
    void avantModification() {
        modifieLe = Instant.now();
    }

    /**
     * Change le statut si la transition est permise.
     *
     * @return vrai si le statut a effectivement change
     */
    public boolean changerStatut(StatutPublication cible) {
        if (!statut.peutPasserA(cible)) {
            throw new ConflitException("TRANSITION_INTERDITE",
                    "Passage de " + statut + " a " + cible + " impossible.");
        }
        if (statut == cible) {
            return false;
        }
        statut = cible;
        return true;
    }

    /**
     * Verrouillage optimiste explicite : la version envoyee par le client est
     * celle qu'il a lue. Si la fiche a change depuis, 409 au lieu d'ecraser
     * silencieusement la modification de l'autre Gerant. Version absente :
     * accepte pour l'instant (le Manager ne l'envoie pas encore, lot 4).
     */
    public void verifierVersion(Long versionLue) {
        if (versionLue != null && versionLue != version) {
            throw new ConflitException("VERSION_CONFLICT",
                    "Cette fiche a ete modifiee entre-temps. Rechargez-la avant d'enregistrer.");
        }
    }

    public Long getId() {
        return id;
    }

    public StatutPublication getStatut() {
        return statut;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public Instant getModifieLe() {
        return modifieLe;
    }

    protected void setStatutInitial(StatutPublication statut) {
        this.statut = statut;
    }
}
