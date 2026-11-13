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
@Table(name = "accompagnements_condiments")
public class AccompagnementCondiment extends ElementPublie {

    @Column(nullable = false, length = 80)
    private String nom;

    /** F CFA, 0 autorise (offert). */
    @Column(nullable = false)
    private long prix;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private TypeAccompagnement type;

    protected AccompagnementCondiment() {
    }

    public AccompagnementCondiment(String nom, long prix, TypeAccompagnement type) {
        modifier(nom, prix, type);
    }

    public void modifier(String nom, long prix, TypeAccompagnement type) {
        this.nom = nom.trim();
        this.prix = prix;
        this.type = type;
    }

    public String getNom() {
        return nom;
    }

    public long getPrix() {
        return prix;
    }

    public TypeAccompagnement getType() {
        return type;
    }
}
