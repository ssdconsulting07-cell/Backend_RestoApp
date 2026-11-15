package sn.chezketchup.backend.catalogue.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import sn.chezketchup.backend.catalogue.domaine.HoraireService;
import sn.chezketchup.backend.catalogue.domaine.JourSemaine;
import sn.chezketchup.backend.catalogue.domaine.Service;
import sn.chezketchup.backend.catalogue.infrastructure.HoraireServiceRepository;
import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.audit.JournalActions.Domaine;
import sn.chezketchup.backend.commun.diffusion.ChangementPublic;

/**
 * Horaires des services Dejeuner / Diner (heure de Dakar) : decident quels
 * plats du jour sont commandables a un instant donne. En dehors de leur
 * service, les plats restent visibles (lecture seule) dans l'App Client.
 */
@Component
public class Horaires {

    /** Valeurs par defaut si la table est vide (base non migree en test). */
    static final LocalTime DEJEUNER_DEBUT = LocalTime.of(11, 0);
    static final LocalTime DEJEUNER_FIN = LocalTime.of(15, 0);
    static final LocalTime DINER_DEBUT = LocalTime.of(18, 0);
    static final LocalTime DINER_FIN = LocalTime.of(1, 0);

    /** Service ouvert et jour auquel il appartient. */
    public record Creneau(Service service, JourSemaine jour) {
    }

    public record Vue(Service service, String debut, String fin, boolean passeMinuit, long version) {
        static Vue de(HoraireService h) {
            return new Vue(h.getService(), h.getDebut().toString(), h.getFin().toString(), h.passeMinuit(),
                    h.getVersion());
        }
    }

    public record Saisie(
            @NotNull Service service,
            @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String debut,
            @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String fin,
            Long version) {
    }

    private final HoraireServiceRepository horaires;
    private final Clock horloge;
    private final JournalActions journal;
    private final ApplicationEventPublisher evenements;

    public Horaires(HoraireServiceRepository horaires, Clock horloge, JournalActions journal,
                    ApplicationEventPublisher evenements) {
        this.horaires = horaires;
        this.horloge = horloge;
        this.journal = journal;
        this.evenements = evenements;
    }

    @Transactional(readOnly = true)
    public List<Vue> lister() {
        return tous().stream().map(Vue::de).toList();
    }

    @Transactional
    public List<Vue> modifier(List<Saisie> saisies) {
        Instant maintenant = Instant.now(horloge);
        for (Saisie s : saisies) {
            HoraireService h = horaires.findById(s.service())
                    .orElseGet(() -> horaires.save(parDefaut(s.service(), maintenant)));
            h.modifier(LocalTime.parse(s.debut()), LocalTime.parse(s.fin()), s.version(), maintenant);
            journal.enregistrer(Domaine.PRODUIT, "HORAIRES", s.service().name(),
                    "a réglé le " + (s.service() == Service.DEJEUNER ? "déjeuner" : "dîner") + " de " + s.debut()
                            + " à " + s.fin());
        }
        evenements.publishEvent(new ChangementPublic(ChangementPublic.Sujet.HORAIRES));
        return lister();
    }

    /** Service ouvert maintenant (heure de Dakar), s'il y en a un. */
    @Transactional(readOnly = true)
    public Optional<Creneau> creneauEnCours() {
        return creneauA(LocalDateTime.now(horloge));
    }

    Optional<Creneau> creneauA(LocalDateTime moment) {
        for (HoraireService h : tous()) {
            Integer decalage = h.decalageJour(moment.toLocalTime());
            if (decalage != null) {
                return Optional.of(new Creneau(h.getService(), jour(moment.toLocalDate().plusDays(decalage))));
            }
        }
        return Optional.empty();
    }

    private List<HoraireService> tous() {
        List<HoraireService> enBase = horaires.findAll();
        Instant maintenant = Instant.now(horloge);
        List<HoraireService> liste = Arrays.stream(Service.values())
                .map(s -> enBase.stream().filter(h -> h.getService() == s).findFirst()
                        .orElseGet(() -> parDefaut(s, maintenant)))
                .toList();
        return liste.stream().sorted(Comparator.comparing(HoraireService::getService)).toList();
    }

    private static HoraireService parDefaut(Service s, Instant maintenant) {
        return s == Service.DEJEUNER
                ? new HoraireService(s, DEJEUNER_DEBUT, DEJEUNER_FIN, maintenant)
                : new HoraireService(s, DINER_DEBUT, DINER_FIN, maintenant);
    }

    public static JourSemaine jour(java.time.LocalDate date) {
        return JourSemaine.values()[date.getDayOfWeek().getValue() - 1];
    }
}
