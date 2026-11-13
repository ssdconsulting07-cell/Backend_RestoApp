package sn.chezketchup.backend.zones.domaine;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sn.chezketchup.backend.commun.erreurs.ConflitException;

/** Region du Senegal ; la livraison y est ouverte quand le Gerant l'active. */
@Entity
@Table(name = "regions")
public class Region {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 60)
    private String nom;

    @Column(nullable = false)
    private boolean active;

    @Version
    private long version;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    protected Region() {
    }

    public Region(String code, String nom, boolean active) {
        this.code = code;
        this.nom = nom;
        this.active = active;
    }

    @PrePersist
    @PreUpdate
    void horodater() {
        modifieLe = Instant.now();
    }

    public void activer(boolean active, Long versionLue) {
        if (versionLue != null && versionLue != version) {
            throw new ConflitException("VERSION_CONFLICT", "Cette region a ete modifiee entre-temps. Rechargez la page.");
        }
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getNom() {
        return nom;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
