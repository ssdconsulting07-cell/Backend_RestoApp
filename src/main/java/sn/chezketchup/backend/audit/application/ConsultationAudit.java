package sn.chezketchup.backend.audit.application;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.audit.domaine.AuditEvenement;
import sn.chezketchup.backend.audit.infrastructure.AuditEvenementRepository;
import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.audit.JournalActions.Domaine;
import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;
import sn.chezketchup.backend.commun.temps.Horloge;
import sn.chezketchup.backend.security.Role;

/** Consultation et export du journal d'audit (Manager). */
@Service
public class ConsultationAudit {

    static final Duration PERIODE_MAX = Duration.ofDays(92);
    static final int TAILLE_MAX = 200;
    static final int LIGNES_EXPORT_MAX = 50_000;
    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(Horloge.DAKAR);
    private static final Map<String, String> ROLES = Map.of("CUISINE", "Cuisine", "GERANT", "Gérant", "MANAGER", "Manager",
            "LIVREUR", "Livreur", "VENDEUR", "Vendeur");
    private static final Map<String, String> DOMAINES = Map.of("COMMANDE", "Commandes", "PRODUIT", "Produits",
            "CATEGORIE", "Catégories", "ANNONCE", "Annonces", "PERSONNEL", "Personnel", "ZONE", "Zones de livraison",
            "PAIEMENT", "Paiements");

    private final AuditEvenementRepository journal;
    private final JournalActions journalActions;
    private final Clock horloge;

    public ConsultationAudit(AuditEvenementRepository journal, JournalActions journalActions, Clock horloge) {
        this.journal = journal;
        this.journalActions = journalActions;
        this.horloge = horloge;
    }

    /** Filtres : periode [du, au] en jours (heure de Dakar), role, type d'action. */
    public record Filtres(LocalDate du, LocalDate au, Role role, Domaine domaine) {
    }

    public record EntreeVue(String id, Instant createdAt, Acteur acteur, Domaine domaine, String code,
                            String cibleId, String action) {

        public record Acteur(String identifiant, String nom, String role) {
        }

        static EntreeVue de(AuditEvenement a) {
            return new EntreeVue(String.valueOf(a.getId()), a.getHorodatage(),
                    new Acteur(a.getActeur(), a.getActeurNom(), a.getRole()),
                    Domaine.valueOf(a.getDomaine()), a.getAction(), a.getCibleId(), a.getMessage());
        }
    }

    public record PageVue(List<EntreeVue> entrees, long total, int page, int taille) {
    }

    @Transactional(readOnly = true)
    public PageVue consulter(Filtres f, int page, int taille) {
        Instant[] p = periode(f);
        Page<AuditEvenement> r = journal.rechercher(p[0], p[1], f.role() == null ? null : f.role().name(),
                f.domaine() == null ? null : f.domaine().name(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, taille), TAILLE_MAX)));
        return new PageVue(r.map(EntreeVue::de).getContent(), r.getTotalElements(), r.getNumber(), r.getSize());
    }

    /**
     * Export CSV pour Excel : UTF-8 avec BOM, separateur « ; », memes colonnes
     * que l'export du Manager. Protection contre l'injection de formules : une
     * cellule commencant par = + - @ (ou tabulation) est prefixee d'une
     * apostrophe, sinon Excel l'executerait a l'ouverture. L'export lui-meme
     * est trace dans le journal.
     */
    @Transactional
    public byte[] exporterCsv(Filtres f) {
        Instant[] p = periode(f);
        List<AuditEvenement> lignes = journal.rechercher(p[0], p[1], f.role() == null ? null : f.role().name(),
                f.domaine() == null ? null : f.domaine().name(), PageRequest.of(0, LIGNES_EXPORT_MAX)).getContent();
        StringBuilder csv = new StringBuilder("Date;Employé;Identifiant;Rôle;Type;Description\r\n");
        for (AuditEvenement a : lignes) {
            csv.append(cellule(FORMAT_DATE.format(a.getHorodatage()))).append(';')
                    .append(cellule(a.getActeurNom())).append(';')
                    .append(cellule(a.getActeur())).append(';')
                    .append(cellule(a.getRole() == null ? "" : ROLES.getOrDefault(a.getRole(), a.getRole()))).append(';')
                    .append(cellule(DOMAINES.getOrDefault(a.getDomaine(), a.getDomaine()))).append(';')
                    .append(cellule(a.getMessage())).append("\r\n");
        }
        journalActions.enregistrer(Domaine.PERSONNEL, "EXPORT_AUDIT", null,
                "a exporté le journal d'audit (" + lignes.size() + " lignes, du " + f.du() + " au " + f.au() + ")");
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        sortie.writeBytes(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        sortie.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
        return sortie.toByteArray();
    }

    /** Echappement CSV + neutralisation des formules. */
    static String cellule(String valeur) {
        String v = valeur == null ? "" : valeur;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private Instant[] periode(Filtres f) {
        LocalDate aujourdhui = LocalDate.now(horloge);
        LocalDate au = f.au() == null ? aujourdhui : f.au();
        LocalDate du = f.du() == null ? au.minusDays(6) : f.du();
        if (du.isAfter(au)) {
            throw new RequeteInvalideException("PERIODE_INVALIDE", "La date de début doit précéder la date de fin.", "du");
        }
        Instant debut = du.atStartOfDay(Horloge.DAKAR).toInstant();
        Instant fin = au.plusDays(1).atStartOfDay(Horloge.DAKAR).toInstant();
        if (Duration.between(debut, fin).compareTo(PERIODE_MAX) > 0) {
            throw new RequeteInvalideException("PERIODE_TROP_LONGUE", "Période limitée à 92 jours.", "du");
        }
        return new Instant[] {debut, fin};
    }
}
