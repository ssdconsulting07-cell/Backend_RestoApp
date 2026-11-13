package sn.chezketchup.backend.statistiques.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;
import sn.chezketchup.backend.commun.temps.Horloge;
import sn.chezketchup.backend.statistiques.infrastructure.StatistiquesRepository;
import sn.chezketchup.backend.statistiques.infrastructure.StatistiquesRepository.LigneCa;
import sn.chezketchup.backend.statistiques.infrastructure.StatistiquesRepository.LigneProduit;
import sn.chezketchup.backend.statistiques.infrastructure.StatistiquesRepository.LigneVente;

/**
 * Statistiques du Manager et du Gerant, avec les chiffres que lisent deja
 * les ecrans du Manager (features/manager : financeData, commandesData).
 * Montants en F CFA entiers.
 */
@Service
@Transactional(readOnly = true)
public class Statistiques {

    static final Duration PERIODE_MAX = Duration.ofDays(366);

    private final StatistiquesRepository repo;
    private final Clock horloge;

    public Statistiques(StatistiquesRepository repo, Clock horloge) {
        this.repo = repo;
        this.horloge = horloge;
    }

    // --- Vues ---------------------------------------------------------------

    public record MontantJour(LocalDate date, long montant) {
    }

    public record ChiffreAffaires(long jour, long semaine, long mois, List<MontantJour> septDerniersJours) {
    }

    public record CommandesDuJour(long total, long encaissees, Map<String, Long> parStatut, long panierMoyen) {
    }

    public record Canal(String canal, long commandes, long montant) {
    }

    /** Tableau de bord du Manager (cartes CA, graphique 7 jours, commandes du jour). */
    public record TableauDeBord(LocalDate date, ChiffreAffaires chiffreAffaires, CommandesDuJour commandesDuJour,
                                List<Canal> parCanalDuJour) {
    }

    public record JourCa(LocalDate date, long commandes, long montant, Map<String, Long> parCanal) {
    }

    public record SerieCa(LocalDate du, LocalDate au, long commandes, long montant, List<JourCa> jours) {
    }

    public record VenteComptoir(LocalDate date, String vendeur, long ventes, long montant, Map<String, Long> parMoyen) {
    }

    // --- Calculs ------------------------------------------------------------------

    public TableauDeBord tableauDeBord() {
        LocalDate aujourdhui = LocalDate.now(horloge);
        LocalDate lundi = aujourdhui.with(DayOfWeek.MONDAY);
        LocalDate premierDuMois = aujourdhui.withDayOfMonth(1);
        LocalDate debut = min(min(lundi, premierDuMois), aujourdhui.minusDays(6));
        List<LigneCa> lignes = repo.caParJourEtCanal(debutDe(debut), debutDe(aujourdhui.plusDays(1)));

        Map<LocalDate, Long> parJour = new TreeMap<>();
        lignes.forEach(l -> parJour.merge(l.date(), l.montant(), Long::sum));
        long jour = parJour.getOrDefault(aujourdhui, 0L);
        long semaine = somme(parJour, lundi, aujourdhui);
        long mois = somme(parJour, premierDuMois, aujourdhui);
        List<MontantJour> sept = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = aujourdhui.minusDays(i);
            sept.add(new MontantJour(d, parJour.getOrDefault(d, 0L)));
        }

        Map<String, Long> parStatut = new LinkedHashMap<>();
        repo.commandesParStatut(debutDe(aujourdhui), debutDe(aujourdhui.plusDays(1)))
                .forEach(s -> parStatut.put(s.statut(), s.commandes()));
        List<Canal> canaux = lignes.stream().filter(l -> l.date().equals(aujourdhui))
                .map(l -> new Canal(l.canal(), l.commandes(), l.montant())).toList();
        long encaissees = canaux.stream().mapToLong(Canal::commandes).sum();
        long total = parStatut.values().stream().mapToLong(Long::longValue).sum();
        long panierMoyen = encaissees == 0 ? 0 : Math.round((double) jour / encaissees);

        return new TableauDeBord(aujourdhui, new ChiffreAffaires(jour, semaine, mois, sept),
                new CommandesDuJour(total, encaissees, parStatut, panierMoyen), canaux);
    }

    public SerieCa chiffreAffaires(LocalDate du, LocalDate au) {
        LocalDate[] p = periode(du, au);
        Map<LocalDate, JourCa> jours = new TreeMap<>();
        for (LigneCa l : repo.caParJourEtCanal(debutDe(p[0]), debutDe(p[1].plusDays(1)))) {
            JourCa j = jours.computeIfAbsent(l.date(), d -> new JourCa(d, 0, 0, new LinkedHashMap<>()));
            j.parCanal().put(l.canal(), l.montant());
            jours.put(l.date(), new JourCa(l.date(), j.commandes() + l.commandes(), j.montant() + l.montant(), j.parCanal()));
        }
        List<JourCa> liste = new ArrayList<>(jours.values());
        return new SerieCa(p[0], p[1], liste.stream().mapToLong(JourCa::commandes).sum(),
                liste.stream().mapToLong(JourCa::montant).sum(), liste);
    }

    public List<LigneProduit> topProduits(LocalDate du, LocalDate au, int limite) {
        LocalDate[] p = periode(du, au);
        return repo.topProduits(debutDe(p[0]), debutDe(p[1].plusDays(1)), Math.min(Math.max(1, limite), 50));
    }

    public Map<String, Long> commandesParStatut(LocalDate du, LocalDate au) {
        LocalDate[] p = periode(du, au);
        Map<String, Long> r = new LinkedHashMap<>();
        repo.commandesParStatut(debutDe(p[0]), debutDe(p[1].plusDays(1))).forEach(s -> r.put(s.statut(), s.commandes()));
        return r;
    }

    /** @param vendeur null = tous les vendeurs (Manager, Gerant) ; sinon les ventes du Vendeur connecte. */
    public List<VenteComptoir> ventesComptoir(LocalDate du, LocalDate au, String vendeur) {
        LocalDate[] p = periode(du, au);
        Map<String, VenteComptoir> parJourVendeur = new LinkedHashMap<>();
        for (LigneVente l : repo.ventesComptoir(debutDe(p[0]), debutDe(p[1].plusDays(1)), vendeur)) {
            String cle = l.date() + "|" + l.vendeur();
            VenteComptoir v = parJourVendeur.getOrDefault(cle, new VenteComptoir(l.date(), l.vendeur(), 0, 0, new LinkedHashMap<>()));
            v.parMoyen().merge(l.moyenPaiement(), l.montant(), Long::sum);
            parJourVendeur.put(cle, new VenteComptoir(l.date(), l.vendeur(), v.ventes() + l.ventes(), v.montant() + l.montant(),
                    v.parMoyen()));
        }
        return new ArrayList<>(parJourVendeur.values());
    }

    // --- Utilitaires -------------------------------------------------------------------

    private LocalDate[] periode(LocalDate du, LocalDate au) {
        LocalDate fin = au == null ? LocalDate.now(horloge) : au;
        LocalDate debut = du == null ? fin.minusDays(6) : du;
        if (debut.isAfter(fin)) {
            throw new RequeteInvalideException("PERIODE_INVALIDE", "La date de début doit précéder la date de fin.", "du");
        }
        if (Duration.between(debutDe(debut), debutDe(fin.plusDays(1))).compareTo(PERIODE_MAX) > 0) {
            throw new RequeteInvalideException("PERIODE_TROP_LONGUE", "Période limitée à un an.", "du");
        }
        return new LocalDate[] {debut, fin};
    }

    private static Instant debutDe(LocalDate jour) {
        return jour.atStartOfDay(Horloge.DAKAR).toInstant();
    }

    private static long somme(Map<LocalDate, Long> parJour, LocalDate du, LocalDate au) {
        return parJour.entrySet().stream().filter(e -> !e.getKey().isBefore(du) && !e.getKey().isAfter(au))
                .mapToLong(Map.Entry::getValue).sum();
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
