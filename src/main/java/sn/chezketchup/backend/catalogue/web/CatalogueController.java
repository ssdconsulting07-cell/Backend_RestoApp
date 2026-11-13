package sn.chezketchup.backend.catalogue.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import sn.chezketchup.backend.catalogue.application.AccompagnementCondimentVue;
import sn.chezketchup.backend.catalogue.application.CatalogueService;
import sn.chezketchup.backend.catalogue.application.CategorieVue;
import sn.chezketchup.backend.catalogue.application.PlatDuJourVue;
import sn.chezketchup.backend.catalogue.application.ProduitVue;
import sn.chezketchup.backend.catalogue.domaine.JourSemaine;
import sn.chezketchup.backend.catalogue.domaine.TypeAccompagnement;
import sn.chezketchup.backend.commun.publication.StatutPublication;
import sn.chezketchup.backend.commun.web.CacheHttp;
import sn.chezketchup.backend.commun.web.Identifiants;

/**
 * Lecture du catalogue, sans authentification (App Client). Sans droits
 * Gerant : contenu publie uniquement, quel que soit le parametre statut.
 */
@RestController
@Tag(name = "Catalogue")
public class CatalogueController {

    private final CatalogueService catalogue;

    public CatalogueController(CatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping("/categories")
    @Operation(summary = "Categories du menu (publiees ; toutes pour le Gerant)")
    public ResponseEntity<List<CategorieVue>> categories(@RequestParam(required = false) StatutPublication statut) {
        return CacheHttp.lecture(catalogue.categories(statut));
    }

    @GetMapping("/produits")
    @Operation(summary = "Produits (publies et de categorie publiee ; tous pour le Gerant)")
    public ResponseEntity<List<ProduitVue>> produits(@RequestParam(required = false) StatutPublication statut,
                                                     @RequestParam(required = false) String categorieId) {
        return CacheHttp.lecture(catalogue.produits(statut, Identifiants.champ(categorieId, "categorieId")));
    }

    @GetMapping("/produits/{id}")
    @Operation(summary = "Detail d'un produit (404 s'il n'est pas publie, hors Gerant)")
    public ResponseEntity<ProduitVue> produit(@PathVariable String id) {
        return CacheHttp.lecture(catalogue.produit(Identifiants.chemin(id, "Produit")));
    }

    @GetMapping("/plats-du-jour")
    @Operation(summary = "Plats du jour, filtrables par jour")
    public ResponseEntity<List<PlatDuJourVue>> plats(@RequestParam(required = false) StatutPublication statut,
                                                     @RequestParam(required = false) JourSemaine jour) {
        return CacheHttp.lecture(catalogue.plats(statut, jour));
    }

    @GetMapping("/accompagnements-condiments")
    @Operation(summary = "Accompagnements et condiments, filtrables par type")
    public ResponseEntity<List<AccompagnementCondimentVue>> accompagnements(
            @RequestParam(required = false) StatutPublication statut,
            @RequestParam(required = false) TypeAccompagnement type) {
        return CacheHttp.lecture(catalogue.accompagnements(statut, type));
    }
}
