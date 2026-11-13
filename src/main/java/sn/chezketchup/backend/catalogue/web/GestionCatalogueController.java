package sn.chezketchup.backend.catalogue.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import sn.chezketchup.backend.catalogue.application.AccompagnementCondimentVue;
import sn.chezketchup.backend.catalogue.application.CategorieVue;
import sn.chezketchup.backend.catalogue.application.GestionCatalogueService;
import sn.chezketchup.backend.catalogue.application.PlatDuJourVue;
import sn.chezketchup.backend.catalogue.application.ProduitVue;
import sn.chezketchup.backend.catalogue.application.Saisies;
import sn.chezketchup.backend.commun.web.Identifiants;

/**
 * Ecritures du catalogue, reservees au Gerant. Creation = BROUILLON ;
 * publication par PATCH .../statut. Pas de suppression : on archive.
 * {@code version} dans le corps = verrouillage optimiste (409 si la fiche a
 * change depuis sa lecture).
 */
@RestController
@PreAuthorize("hasRole('GERANT')")
@Tag(name = "Catalogue (Gerant)")
@SecurityRequirement(name = "bearerAuth")
public class GestionCatalogueController {

    private final GestionCatalogueService gestion;

    public GestionCatalogueController(GestionCatalogueService gestion) {
        this.gestion = gestion;
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer une categorie (brouillon)")
    public CategorieVue creerCategorie(@Valid @RequestBody Saisies.Categorie saisie) {
        return gestion.creerCategorie(saisie);
    }

    @PutMapping("/categories/{id}")
    public CategorieVue modifierCategorie(@PathVariable String id, @Valid @RequestBody Saisies.Categorie saisie) {
        return gestion.modifierCategorie(Identifiants.chemin(id, "Categorie"), saisie);
    }

    @PatchMapping("/categories/{id}/statut")
    @Operation(summary = "Publier / depublier / archiver / restaurer (archivage d'une categorie non vide : reassignerVers)")
    public CategorieVue statutCategorie(@PathVariable String id, @Valid @RequestBody Saisies.ChangementStatut saisie) {
        return gestion.changerStatutCategorie(Identifiants.chemin(id, "Categorie"), saisie);
    }

    @PostMapping("/produits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer un produit (brouillon)")
    public ProduitVue creerProduit(@Valid @RequestBody Saisies.Produit saisie) {
        return gestion.creerProduit(saisie);
    }

    @PutMapping("/produits/{id}")
    @Operation(summary = "Modifier un produit (dont prix, rupture, tendance)")
    public ProduitVue modifierProduit(@PathVariable String id, @Valid @RequestBody Saisies.Produit saisie) {
        return gestion.modifierProduit(Identifiants.chemin(id, "Produit"), saisie);
    }

    @PatchMapping("/produits/{id}/statut")
    public ProduitVue statutProduit(@PathVariable String id, @Valid @RequestBody Saisies.ChangementStatut saisie) {
        return gestion.changerStatutProduit(Identifiants.chemin(id, "Produit"), saisie);
    }

    @PostMapping("/plats-du-jour")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatDuJourVue creerPlat(@Valid @RequestBody Saisies.PlatDuJour saisie) {
        return gestion.creerPlat(saisie);
    }

    @PutMapping("/plats-du-jour/{id}")
    public PlatDuJourVue modifierPlat(@PathVariable String id, @Valid @RequestBody Saisies.PlatDuJour saisie) {
        return gestion.modifierPlat(Identifiants.chemin(id, "Plat du jour"), saisie);
    }

    @PatchMapping("/plats-du-jour/{id}/statut")
    public PlatDuJourVue statutPlat(@PathVariable String id, @Valid @RequestBody Saisies.ChangementStatut saisie) {
        return gestion.changerStatutPlat(Identifiants.chemin(id, "Plat du jour"), saisie);
    }

    @PostMapping("/accompagnements-condiments")
    @ResponseStatus(HttpStatus.CREATED)
    public AccompagnementCondimentVue creerAccompagnement(@Valid @RequestBody Saisies.AccompagnementCondiment saisie) {
        return gestion.creerAccompagnement(saisie);
    }

    @PutMapping("/accompagnements-condiments/{id}")
    public AccompagnementCondimentVue modifierAccompagnement(@PathVariable String id,
                                                             @Valid @RequestBody Saisies.AccompagnementCondiment saisie) {
        return gestion.modifierAccompagnement(Identifiants.chemin(id, "Fiche"), saisie);
    }

    @PatchMapping("/accompagnements-condiments/{id}/statut")
    public AccompagnementCondimentVue statutAccompagnement(@PathVariable String id,
                                                           @Valid @RequestBody Saisies.ChangementStatut saisie) {
        return gestion.changerStatutAccompagnement(Identifiants.chemin(id, "Fiche"), saisie);
    }
}
