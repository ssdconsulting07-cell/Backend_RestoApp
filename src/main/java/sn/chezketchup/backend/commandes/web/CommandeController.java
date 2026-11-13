package sn.chezketchup.backend.commandes.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import sn.chezketchup.backend.commandes.application.ChangementStatutCommande;
import sn.chezketchup.backend.commandes.application.CommandeSaisie;
import sn.chezketchup.backend.commandes.application.CommandeVue;
import sn.chezketchup.backend.commandes.application.CreationCommande;
import sn.chezketchup.backend.commandes.application.SuiviCommandes;
import sn.chezketchup.backend.commandes.domaine.Enums.Canal;
import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;
import sn.chezketchup.backend.commun.securite.UtilisateurCourant;
import sn.chezketchup.backend.commun.web.Identifiants;

@RestController
@RequestMapping("/commandes")
@Tag(name = "Commandes")
public class CommandeController {

    /** En-tete du jeton de suivi (jamais en parametre d'URL : il finirait dans les journaux). */
    public static final String EN_TETE_JETON = "X-Jeton-Suivi";

    private final CreationCommande creation;
    private final SuiviCommandes suivi;
    private final ChangementStatutCommande changement;

    public CommandeController(CreationCommande creation, SuiviCommandes suivi, ChangementStatutCommande changement) {
        this.creation = creation;
        this.suivi = suivi;
        this.changement = changement;
    }

    @PostMapping
    @Operation(summary = "Creer une commande (APP : public ; COMPTOIR : Vendeur). Prix et totaux recalcules par le serveur.")
    public ResponseEntity<String> creer(
            @Valid @RequestBody CommandeSaisie saisie,
            @Parameter(description = "Cle unique par commande : un renvoi avec la meme cle ne cree pas de doublon")
            @RequestHeader(name = "Idempotency-Key", required = false) String cleIdempotence) {
        CreationCommande.Resultat r = creation.creer(saisie, cleIdempotence);
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .header("Idempotency-Replayed", String.valueOf(r.rejouee()))
                .body(r.corpsJson());
    }

    @GetMapping
    @Operation(summary = "Staff : sa file de travail. Client (sans jeton) : ses commandes par numero, en vue reduite.")
    public ResponseEntity<List<CommandeVue>> lister(
            @RequestParam(required = false) String clientTelephone,
            @RequestParam(required = false) StatutCommande statut,
            @RequestParam(required = false) Canal canal,
            @RequestParam(required = false) Instant depuis) {
        List<CommandeVue> liste = UtilisateurCourant.role()
                .map(role -> suivi.pourStaff(role, statut, canal, depuis))
                .orElseGet(() -> suivi.parTelephone(clientTelephone));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(liste);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Suivi d'une commande : staff selon sa file, client avec son jeton (en-tete X-Jeton-Suivi)")
    public ResponseEntity<CommandeVue> consulter(@PathVariable String id,
                                                 @RequestHeader(name = EN_TETE_JETON, required = false) String jeton) {
        long numero = Identifiants.chemin(id, "Commande");
        CommandeVue vue = UtilisateurCourant.role()
                .map(role -> suivi.pourStaff(numero, role))
                .orElseGet(() -> suivi.pourClient(numero, jeton));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vue);
    }

    public record DemandeStatut(@NotNull StatutCommande statut, Long version) {
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('CUISINE', 'LIVREUR', 'VENDEUR', 'MANAGER', 'GERANT')")
    @Operation(summary = "Transition de statut (table par role et par mode ; 403 hors table, 409 si deja appliquee)")
    public CommandeVue changerStatut(@PathVariable String id, @Valid @RequestBody DemandeStatut demande) {
        return changement.appliquer(Identifiants.chemin(id, "Commande"), demande.statut(), demande.version(),
                UtilisateurCourant.role().orElseThrow(), UtilisateurCourant.identifiant().orElseThrow());
    }
}
