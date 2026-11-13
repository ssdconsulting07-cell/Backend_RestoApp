package sn.chezketchup.backend.audit.domaine;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Entree du journal d'audit (lecture seule une fois ecrite : aucun setter). */
@Entity
@Table(name = "audit_evenements")
public class AuditEvenement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant horodatage;

    @Column(nullable = false)
    private String acteur;

    @Column(name = "acteur_nom", nullable = false, length = 130)
    private String acteurNom;

    @Column(length = 20)
    private String role;

    @Column(nullable = false, length = 20)
    private String domaine;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "cible_id", length = 100)
    private String cibleId;

    @Column(nullable = false, length = 500)
    private String message;

    protected AuditEvenement() {
    }

    public AuditEvenement(Instant horodatage, String acteur, String acteurNom, String role, String domaine,
                          String action, String cibleId, String message) {
        this.horodatage = horodatage;
        this.acteur = acteur;
        this.acteurNom = acteurNom;
        this.role = role;
        this.domaine = domaine;
        this.action = action;
        this.cibleId = cibleId;
        this.message = message.length() > 500 ? message.substring(0, 497) + "..." : message;
    }

    public Long getId() {
        return id;
    }

    public Instant getHorodatage() {
        return horodatage;
    }

    public String getActeur() {
        return acteur;
    }

    public String getActeurNom() {
        return acteurNom;
    }

    public String getRole() {
        return role;
    }

    public String getDomaine() {
        return domaine;
    }

    public String getAction() {
        return action;
    }

    public String getCibleId() {
        return cibleId;
    }

    public String getMessage() {
        return message;
    }
}
