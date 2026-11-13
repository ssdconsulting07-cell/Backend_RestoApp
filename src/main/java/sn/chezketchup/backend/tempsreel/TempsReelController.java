package sn.chezketchup.backend.tempsreel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import sn.chezketchup.backend.commandes.infrastructure.CommandeRepository;
import sn.chezketchup.backend.commun.securite.UtilisateurCourant;

/**
 * Flux temps reel des commandes (Server-Sent Events), reserve au staff
 * authentifie. Le Manager s'y connecte avec fetch() en streaming pour
 * envoyer son en-tete Authorization (un EventSource de navigateur ne le
 * peut pas, et un jeton dans l'URL finirait dans les journaux).
 *
 * Reconnexion : le navigateur renvoie Last-Event-ID ; les evenements manques
 * (24 dernieres heures) sont renvoyes avant le direct.
 *
 * Derriere nginx : bloc dedie (proxy_buffering off, delai long), voir le
 * document "Reste a faire".
 */
@RestController
@Tag(name = "Temps reel")
public class TempsReelController {

    static final Duration DUREE_CONNEXION = Duration.ofMinutes(30);
    static final Duration RATTRAPAGE = Duration.ofHours(24);

    private final FluxCommandes flux;
    private final EvenementSortantRepository evenements;
    private final CommandeRepository commandes;
    private final Clock horloge;

    public TempsReelController(FluxCommandes flux, EvenementSortantRepository evenements,
                               CommandeRepository commandes, Clock horloge) {
        this.flux = flux;
        this.evenements = evenements;
        this.commandes = commandes;
        this.horloge = horloge;
    }

    @GetMapping(path = "/temps-reel/commandes", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('CUISINE', 'LIVREUR', 'VENDEUR', 'MANAGER', 'GERANT')")
    @Operation(summary = "Flux SSE des commandes de la file du role (evenement 'commande')")
    @Transactional(readOnly = true)
    public SseEmitter abonner(@RequestHeader(name = "Last-Event-ID", required = false) String dernierEvenement) {
        var role = UtilisateurCourant.role().orElseThrow();
        SseEmitter emetteur = new SseEmitter(DUREE_CONNEXION.toMillis());
        try {
            // Premier message immediat : les en-tetes partent tout de suite (le poste
            // sait qu'il est connecte) et le navigateur apprend le delai de
            // reconnexion a utiliser en cas de coupure.
            emetteur.send(SseEmitter.event().comment("connecte").reconnectTime(5_000));
        } catch (java.io.IOException deconnecte) {
            emetteur.completeWithError(deconnecte);
            return emetteur;
        }
        if (dernierEvenement != null && dernierEvenement.matches("\\d{1,18}")) {
            Instant depuis = Instant.now(horloge).minus(RATTRAPAGE);
            evenements.publiesApres(Long.parseLong(dernierEvenement), depuis).stream().limit(200)
                    .forEach(e -> commandes.findById(e.getCommandeId())
                            .ifPresent(c -> flux.envoyer(emetteur, role, e.getId(), e.getType(), c)));
        }
        flux.abonner(emetteur, role);
        return emetteur;
    }
}
