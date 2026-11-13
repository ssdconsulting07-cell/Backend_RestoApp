package sn.chezketchup.backend.tempsreel;

import java.time.Clock;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.commandes.infrastructure.CommandeRepository;

/**
 * Publie les evenements de l'outbox vers les postes connectes, puis les
 * marque publies. Toutes les secondes : delai percu negligeable pour la
 * cuisine, et aucune commande enregistree ne peut manquer son evenement.
 */
@Component
public class RelaisEvenements {

    private final EvenementSortantRepository evenements;
    private final CommandeRepository commandes;
    private final FluxCommandes flux;
    private final Clock horloge;

    public RelaisEvenements(EvenementSortantRepository evenements, CommandeRepository commandes, FluxCommandes flux,
                            Clock horloge) {
        this.evenements = evenements;
        this.commandes = commandes;
        this.flux = flux;
        this.horloge = horloge;
    }

    @Scheduled(fixedDelay = 1_000)
    @Transactional
    public void relayer() {
        for (EvenementSortant e : evenements.aPublier()) {
            commandes.findById(e.getCommandeId()).ifPresent(c -> flux.diffuser(e.getId(), e.getType(), c));
            e.marquerPublie(Instant.now(horloge));
        }
    }
}
