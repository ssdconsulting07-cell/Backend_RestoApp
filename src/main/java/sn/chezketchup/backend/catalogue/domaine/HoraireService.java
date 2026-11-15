package sn.chezketchup.backend.catalogue.domaine;

import java.time.Instant;
import java.time.LocalTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sn.chezketchup.backend.commun.erreurs.ConflitException;
import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;

/**
 * Heures d'un service (Dejeuner, Diner), reglables par le Gerant. Un service
 * peut finir apres minuit (fin avant debut, ex. 18:00 - 01:00) : il
 * appartient alors au jour ou il a commence.
 */
@Entity
@Table(name = "horaires_services")
public class HoraireService {

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private Service service;

    @Column(nullable = false)
    private LocalTime debut;

    @Column(nullable = false)
    private LocalTime fin;

    @Version
    private long version;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    protected HoraireService() {
    }

    public HoraireService(Service service, LocalTime debut, LocalTime fin, Instant maintenant) {
        this.service = service;
        modifier(debut, fin, null, maintenant);
    }

    public void modifier(LocalTime debut, LocalTime fin, Long versionLue, Instant maintenant) {
        if (versionLue != null && versionLue != version) {
            throw new ConflitException("VERSION_CONFLICT",
                    "Ces horaires ont ete modifies entre-temps. Rechargez-les avant d'enregistrer.");
        }
        if (debut == null || fin == null || debut.equals(fin)) {
            throw new RequeteInvalideException("HORAIRES_INVALIDES",
                    "L'heure de fin doit être différente de l'heure de début.", "fin");
        }
        this.debut = debut;
        this.fin = fin;
        this.modifieLe = maintenant;
    }

    /** Le service passe minuit (ex. 18:00 - 01:00). */
    public boolean passeMinuit() {
        return fin.isBefore(debut);
    }

    /**
     * Jour de service correspondant a l'heure donnee, ou null si le service
     * n'est pas ouvert : {@code 0} = le jour meme, {@code -1} = la veille
     * (partie apres minuit d'un service qui passe minuit).
     */
    public Integer decalageJour(LocalTime heure) {
        if (!passeMinuit()) {
            return !heure.isBefore(debut) && heure.isBefore(fin) ? 0 : null;
        }
        if (!heure.isBefore(debut)) {
            return 0;
        }
        return heure.isBefore(fin) ? -1 : null;
    }

    public Service getService() {
        return service;
    }

    public LocalTime getDebut() {
        return debut;
    }

    public LocalTime getFin() {
        return fin;
    }

    public long getVersion() {
        return version;
    }
}
