package sn.chezketchup.backend.commandes.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.commandes.domaine.Commande;
import sn.chezketchup.backend.commandes.domaine.Enums.Canal;
import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;
import sn.chezketchup.backend.commandes.infrastructure.CommandeRepository;
import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;
import sn.chezketchup.backend.commun.erreurs.RessourceIntrouvableException;
import sn.chezketchup.backend.security.Role;

/** Lecture des commandes : files du staff, suivi du client. */
@Service
@Transactional(readOnly = true)
public class SuiviCommandes {

    static final Duration PERIODE_PAR_DEFAUT = Duration.ofHours(48);
    static final Duration PERIODE_MAX = Duration.ofDays(31);
    static final Duration HISTORIQUE_CLIENT = Duration.ofDays(90);
    static final int LIMITE_STAFF = 500;

    private final CommandeRepository commandes;
    private final Clock horloge;

    public SuiviCommandes(CommandeRepository commandes, Clock horloge) {
        this.commandes = commandes;
        this.horloge = horloge;
    }

    /** File de travail du role, plus recentes d'abord. */
    public List<CommandeVue> pourStaff(Role role, StatutCommande statut, Canal canal, Instant depuis) {
        Instant maintenant = Instant.now(horloge);
        Instant debut = depuis == null ? maintenant.minus(PERIODE_PAR_DEFAUT) : depuis;
        if (debut.isBefore(maintenant.minus(PERIODE_MAX))) {
            debut = maintenant.minus(PERIODE_MAX);
        }
        Set<StatutCommande> statuts = EnumSet.copyOf(VisibiliteCommandes.statutsDe(role));
        if (statut != null) {
            statuts.retainAll(EnumSet.of(statut));
            if (statuts.isEmpty()) {
                return List.of();
            }
        }
        return commandes.pourStaff(debut, statuts, VisibiliteCommandes.modeDe(role), canal,
                        PageRequest.of(0, LIMITE_STAFF))
                .stream().map(c -> CommandeVue.pourStaff(c, role)).toList();
    }

    /** Client sans jeton : ses commandes par numero, en vue reduite (90 derniers jours). */
    public List<CommandeVue> parTelephone(String saisie) {
        String telephone = Telephone.normaliser(saisie).orElseThrow(() -> new RequeteInvalideException(
                "TELEPHONE_REQUIS", "Indiquez un numero senegalais a 9 chiffres.", "clientTelephone"));
        return commandes.findTop50ByClientTelephoneAndCreeLeAfterOrderByCreeLeDesc(telephone,
                        Instant.now(horloge).minus(HISTORIQUE_CLIENT))
                .stream().map(CommandeVue::reduite).toList();
    }

    public CommandeVue pourStaff(long id, Role role) {
        Commande c = commandes.findById(id).filter(x -> VisibiliteCommandes.voit(role, x))
                .orElseThrow(SuiviCommandes::introuvable);
        return CommandeVue.pourStaff(c, role);
    }

    /**
     * Client : uniquement avec le jeton de suivi de cette commande. Sans jeton
     * ou jeton faux : 404 (on ne confirme meme pas que le numero existe).
     */
    public CommandeVue pourClient(long id, String jeton) {
        Commande c = commandes.findById(id).filter(x -> JetonSuivi.correspond(jeton, x.getJetonSuiviEmpreinte()))
                .orElseThrow(SuiviCommandes::introuvable);
        return CommandeVue.pourClient(c);
    }

    private static RessourceIntrouvableException introuvable() {
        return new RessourceIntrouvableException("Commande introuvable.");
    }
}
