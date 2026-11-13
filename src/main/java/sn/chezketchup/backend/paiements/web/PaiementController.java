package sn.chezketchup.backend.paiements.web;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import sn.chezketchup.backend.commandes.web.CommandeController;
import sn.chezketchup.backend.commun.web.Identifiants;
import sn.chezketchup.backend.paiements.application.SuiviPaiements;
import sn.chezketchup.backend.paiements.application.SuiviPaiements.PaiementInitVue;

@RestController
@RequestMapping("/paiements")
@Tag(name = "Paiements")
public class PaiementController {

    private final SuiviPaiements suivi;

    public PaiementController(SuiviPaiements suivi) {
        this.suivi = suivi;
    }

    public record DemandeInit(@NotBlank String commandeId) {
    }

    @PostMapping("/init")
    @Operation(summary = "Relancer le paiement d'une commande en attente (en-tete X-Jeton-Suivi obligatoire)")
    public ResponseEntity<PaiementInitVue> initier(@Valid @RequestBody DemandeInit demande,
                                                   @RequestHeader(name = CommandeController.EN_TETE_JETON, required = false) String jeton) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(suivi.relancer(Identifiants.chemin(demande.commandeId(), "Commande"), jeton));
    }

    /**
     * Notification du prestataire. Corps lu brut : la signature porte sur les
     * octets exacts. Repond 200 des que la notification est authentifiee (meme
     * deja traitee ou sans effet) pour que le prestataire cesse ses envois ;
     * 401 si la signature est invalide.
     */
    @PostMapping("/webhook")
    @Operation(summary = "Notification signee du prestataire de paiement (traitee une seule fois)")
    public Map<String, String> webhook(@RequestBody byte[] corps, @RequestHeader Map<String, String> entetes) {
        return Map.of("resultat", suivi.traiterNotification(corps, entetes).name());
    }
}
