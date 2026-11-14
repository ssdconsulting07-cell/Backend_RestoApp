package sn.chezketchup.backend.avis.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import sn.chezketchup.backend.avis.application.AvisLivraisonService;
import sn.chezketchup.backend.avis.application.AvisLivraisonService.Saisie;
import sn.chezketchup.backend.avis.application.AvisLivraisonService.Vue;
import sn.chezketchup.backend.commandes.web.CommandeController;
import sn.chezketchup.backend.commun.web.Identifiants;

/** Avis du client sur sa livraison (jeton de suivi requis). */
@RestController
@RequestMapping("/commandes/{id}/avis")
@Tag(name = "Commandes")
public class AvisLivraisonController {

    private final AvisLivraisonService avis;

    public AvisLivraisonController(AvisLivraisonService avis) {
        this.avis = avis;
    }

    @PostMapping
    @Operation(summary = "Noter la livraison (1 à 5) : client, avec son jeton de suivi, une fois la commande livrée")
    public ResponseEntity<Vue> donner(@PathVariable String id,
                                      @RequestHeader(name = CommandeController.EN_TETE_JETON, required = false) String jeton,
                                      @Valid @RequestBody Saisie saisie) {
        Vue vue = avis.donner(Identifiants.chemin(id, "Commande"), jeton, saisie);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(vue);
    }

    @GetMapping
    @Operation(summary = "Avis déjà donné sur cette livraison (404 sinon) : client, avec son jeton de suivi")
    public ResponseEntity<Vue> consulter(@PathVariable String id,
                                         @RequestHeader(name = CommandeController.EN_TETE_JETON, required = false) String jeton) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(avis.consulter(Identifiants.chemin(id, "Commande"), jeton));
    }
}
