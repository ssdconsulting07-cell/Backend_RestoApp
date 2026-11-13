package sn.chezketchup.backend.statistiques.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import sn.chezketchup.backend.commun.securite.UtilisateurCourant;
import sn.chezketchup.backend.security.Role;
import sn.chezketchup.backend.statistiques.application.Statistiques;
import sn.chezketchup.backend.statistiques.infrastructure.StatistiquesRepository.LigneProduit;

/**
 * Statistiques : Manager et Gerant. Ventes comptoir : aussi le Vendeur, qui
 * ne voit que les siennes. Periode par defaut : les 7 derniers jours.
 */
@RestController
@RequestMapping("/statistiques")
@Tag(name = "Statistiques")
public class StatistiquesController {

    private final Statistiques statistiques;

    public StatistiquesController(Statistiques statistiques) {
        this.statistiques = statistiques;
    }

    @GetMapping("/tableau-de-bord")
    @PreAuthorize("hasAnyRole('MANAGER', 'GERANT')")
    @Operation(summary = "CA du jour / de la semaine / du mois, 7 derniers jours, commandes du jour par statut, panier moyen")
    public Statistiques.TableauDeBord tableauDeBord() {
        return statistiques.tableauDeBord();
    }

    @GetMapping("/chiffre-affaires")
    @PreAuthorize("hasAnyRole('MANAGER', 'GERANT')")
    @Operation(summary = "Chiffre d'affaires encaisse par jour et par canal (APP / COMPTOIR)")
    public Statistiques.SerieCa chiffreAffaires(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        return statistiques.chiffreAffaires(du, au);
    }

    @GetMapping("/top-produits")
    @PreAuthorize("hasAnyRole('MANAGER', 'GERANT')")
    @Operation(summary = "Produits et plats du jour les plus vendus (quantite, montant)")
    public List<LigneProduit> topProduits(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(defaultValue = "10") int limite) {
        return statistiques.topProduits(du, au, limite);
    }

    @GetMapping("/commandes-par-statut")
    @PreAuthorize("hasAnyRole('MANAGER', 'GERANT')")
    public Map<String, Long> commandesParStatut(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        return statistiques.commandesParStatut(du, au);
    }

    @GetMapping("/ventes-comptoir")
    @PreAuthorize("hasAnyRole('MANAGER', 'GERANT', 'VENDEUR')")
    @Operation(summary = "Ventes comptoir par jour et par vendeur (le Vendeur ne voit que les siennes)")
    public List<Statistiques.VenteComptoir> ventesComptoir(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        String vendeur = UtilisateurCourant.role().filter(r -> r == Role.VENDEUR)
                .flatMap(r -> UtilisateurCourant.identifiant()).orElse(null);
        return statistiques.ventesComptoir(du, au, vendeur);
    }
}
