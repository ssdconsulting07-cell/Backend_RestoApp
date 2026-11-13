package sn.chezketchup.backend.annonces.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import sn.chezketchup.backend.annonces.application.AnnonceSaisie;
import sn.chezketchup.backend.annonces.application.AnnonceService;
import sn.chezketchup.backend.annonces.application.AnnonceVue;
import sn.chezketchup.backend.catalogue.application.Saisies;
import sn.chezketchup.backend.commun.publication.StatutPublication;
import sn.chezketchup.backend.commun.web.CacheHttp;
import sn.chezketchup.backend.commun.web.Identifiants;

@RestController
@RequestMapping("/annonces")
@Tag(name = "Annonces")
public class AnnonceController {

    private final AnnonceService service;

    public AnnonceController(AnnonceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Annonces publiees (toutes pour le Gerant)")
    public ResponseEntity<List<AnnonceVue>> lister(@RequestParam(required = false) StatutPublication statut) {
        return CacheHttp.lecture(service.lister(statut));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GERANT')")
    public AnnonceVue creer(@Valid @RequestBody AnnonceSaisie saisie) {
        return service.creer(saisie);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GERANT')")
    public AnnonceVue modifier(@PathVariable String id, @Valid @RequestBody AnnonceSaisie saisie) {
        return service.modifier(Identifiants.chemin(id, "Annonce"), saisie);
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Publier (date de publication renouvelee : la popup reapparait), depublier, archiver")
    public AnnonceVue statut(@PathVariable String id, @Valid @RequestBody Saisies.ChangementStatut saisie) {
        return service.changerStatut(Identifiants.chemin(id, "Annonce"), saisie.statut(), saisie.version());
    }
}
