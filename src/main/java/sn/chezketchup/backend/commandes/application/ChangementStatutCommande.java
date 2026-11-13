package sn.chezketchup.backend.commandes.application;

import static sn.chezketchup.backend.commun.audit.Libelles.fcfa;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.commandes.domaine.Commande;
import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;
import sn.chezketchup.backend.commandes.infrastructure.CommandeRepository;
import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.audit.JournalActions.Domaine;
import sn.chezketchup.backend.commun.erreurs.ConflitException;
import sn.chezketchup.backend.commun.erreurs.RessourceIntrouvableException;
import sn.chezketchup.backend.security.Role;
import sn.chezketchup.backend.tempsreel.EvenementSortant;
import sn.chezketchup.backend.tempsreel.EvenementSortantRepository;

/**
 * Transition de statut par le staff, sous verrou exclusif de la commande.
 *
 * Concurrence : la Cuisine et le Livreur passent la meme commande de PRETE a
 * EN_LIVRAISON au meme instant. Le premier prend le verrou et applique la
 * transition ; le second attend, relit la commande deja EN_LIVRAISON et
 * recoit 409 STATUT_DEJA_APPLIQUE. Jamais deux transitions concurrentes
 * appliquees.
 */
@Service
public class ChangementStatutCommande {

    private final CommandeRepository commandes;
    private final EvenementSortantRepository evenements;
    private final JournalActions journal;
    private final Clock horloge;

    public ChangementStatutCommande(CommandeRepository commandes, EvenementSortantRepository evenements,
                                    JournalActions journal, Clock horloge) {
        this.commandes = commandes;
        this.evenements = evenements;
        this.journal = journal;
        this.horloge = horloge;
    }

    @Transactional
    public CommandeVue appliquer(long id, StatutCommande cible, Long versionLue, Role role, String acteur) {
        Commande c = commandes.verrouiller(id)
                .filter(x -> VisibiliteCommandes.voit(role, x))
                .orElseThrow(() -> new RessourceIntrouvableException("Commande introuvable."));
        if (versionLue != null && versionLue != c.getVersion()) {
            throw new ConflitException("VERSION_CONFLICT",
                    "Cette commande a change entre-temps. Actualisez la liste.");
        }
        Instant maintenant = Instant.now(horloge);
        c.appliquerTransition(cible, role, acteur, maintenant);
        evenements.save(new EvenementSortant(EvenementSortant.Type.STATUT_CHANGE, c.getId(), maintenant));
        journal.enregistrer(Domaine.COMMANDE, "STATUT_" + cible, String.valueOf(id), message(c, cible));
        commandes.flush();
        return CommandeVue.pourStaff(c, role);
    }

    static String message(Commande c, StatutCommande cible) {
        String n = "#" + c.getId();
        return switch (cible) {
            case EN_PREPARATION -> "a passé la commande " + n + " en préparation";
            case PRETE -> "a marqué la commande " + n + " comme prête";
            case EN_LIVRAISON -> "a lancé la livraison de la commande " + n;
            case LIVREE -> "a marqué la commande " + n + " comme livrée";
            case RETIREE -> "a remis la commande " + n + " au client";
            case ANNULEE -> "a annulé la commande " + n + (c.isRemboursementASignaler()
                    ? " — remboursement de " + fcfa(c.getTotal()) + " à signaler" : "");
            default -> "a modifié la commande " + n;
        };
    }
}
