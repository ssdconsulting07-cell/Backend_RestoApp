package sn.chezketchup.backend.zones.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import sn.chezketchup.backend.commun.web.CacheHttp;
import sn.chezketchup.backend.zones.application.ZonesService;
import sn.chezketchup.backend.zones.application.ZonesVues;
import sn.chezketchup.backend.zones.application.ZonesVues.RegionVue;
import sn.chezketchup.backend.zones.application.ZonesVues.SecteurVue;

@RestController
@Tag(name = "Zones de livraison")
public class ZonesController {

    private final ZonesService zones;

    public ZonesController(ZonesService zones) {
        this.zones = zones;
    }

    @GetMapping("/regions")
    @Operation(summary = "Regions du Senegal (?active=true : livraison ouverte)")
    public ResponseEntity<List<RegionVue>> regions(@RequestParam(required = false) Boolean active) {
        return CacheHttp.lecture(zones.regions(active));
    }

    @GetMapping("/regions/{code}/secteurs")
    @Operation(summary = "Zones d'une region (client : zones proposees ; Gerant : toutes, masquees comprises)")
    public ResponseEntity<List<SecteurVue>> secteurs(@PathVariable String code) {
        return CacheHttp.lecture(zones.secteurs(code));
    }

    @PatchMapping("/regions/{code}")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Ouvrir / fermer la livraison dans une region")
    public RegionVue activer(@PathVariable String code, @Valid @RequestBody ZonesVues.ActivationRegion saisie) {
        return zones.activerRegion(code, saisie);
    }

    @PostMapping("/regions/{code}/secteurs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Ajouter une zone a la main (sans centre GPS)")
    public SecteurVue ajouter(@PathVariable String code, @Valid @RequestBody ZonesVues.NouveauSecteur saisie) {
        return zones.ajouterSecteur(code, saisie);
    }

    @PutMapping("/secteurs/{id}")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Modifier le prix d'une zone (et le nom d'une zone ajoutee a la main)")
    public SecteurVue modifier(@PathVariable String id, @Valid @RequestBody ZonesVues.ModificationSecteur saisie) {
        return zones.modifierSecteur(id, saisie);
    }

    @PatchMapping("/secteurs/{id}/masque")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Masquer / reafficher une zone")
    public SecteurVue masquer(@PathVariable String id, @Valid @RequestBody ZonesVues.MasquageSecteur saisie) {
        return zones.masquerSecteur(id, saisie);
    }

    @DeleteMapping("/secteurs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Supprimer une zone ajoutee a la main (arrondissement officiel : 409, a masquer)")
    public void supprimer(@PathVariable String id) {
        zones.supprimerSecteur(id);
    }
}
