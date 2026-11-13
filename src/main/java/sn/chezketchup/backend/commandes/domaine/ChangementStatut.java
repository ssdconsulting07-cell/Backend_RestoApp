package sn.chezketchup.backend.commandes.domaine;

import java.time.Instant;

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
import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;

/** Horodatage de chaque passage de statut, avec son auteur (tracabilite). */
@Entity
@Table(name = "historique_statuts_commande")
public class ChangementStatut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private StatutCommande statut;

    @Column(nullable = false)
    private Instant horodatage;

    @Column(nullable = false)
    private String acteur;

    @Column(length = 20)
    private String role;

    protected ChangementStatut() {
    }

    ChangementStatut(Commande commande, StatutCommande statut, Instant horodatage, String acteur, String role) {
        this.commande = commande;
        this.statut = statut;
        this.horodatage = horodatage;
        this.acteur = acteur;
        this.role = role;
    }

    public StatutCommande getStatut() {
        return statut;
    }

    public Instant getHorodatage() {
        return horodatage;
    }

    public String getActeur() {
        return acteur;
    }

    public String getRole() {
        return role;
    }
}
