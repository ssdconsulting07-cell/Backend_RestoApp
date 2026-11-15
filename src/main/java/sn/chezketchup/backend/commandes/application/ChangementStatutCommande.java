package sn.chezketchup.backend.commandes.application;

import static sn.chezketchup.backend.commun.audit.Libelles.fcfa;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.auth.StaffUser;
import sn.chezketchup.backend.auth.StaffUserRepository;
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
    private final StaffUserRepository personnel;

    public ChangementStatutCommande(CommandeRepository commandes, EvenementSortantRepository evenements,
                                    JournalActions journal, Clock horloge, StaffUserRepository personnel) {
        this.personnel = personnel;
        this.commandes = commandes;
        this.evenements = evenements;
        this.journal = journal;
        this.horloge = horloge;
    }

    /**
     * Depart en livraison : la commande n'a pas encore de livreur ; le
     * premier livreur qui la prend l'obtient (prendreEnCharge). Si un livreur
     * fait lui-meme passer la commande en livraison, elle est a lui.
     * Seul le livreur attribue peut la marquer livree.
     */
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
        if (cible == StatutCommande.LIVREE && role == Role.LIVREUR && c.getLivreurId() != null) {
            StaffUser moi = personnel.findByUsernameIgnoreCase(acteur).orElse(null);
            if (moi == null || !moi.getId().equals(c.getLivreurId())) {
                throw new ConflitException("COMMANDE_AUTRE_LIVREUR",
                        "Cette commande est livrée par " + c.getLivreurNom() + ".");
            }
        }
        c.appliquerTransition(cible, role, acteur, maintenant);
        if (cible == StatutCommande.EN_LIVRAISON && role == Role.LIVREUR) {
            personnel.findByUsernameIgnoreCase(acteur)
                    .ifPresent(l -> c.attribuerLivreur(l.getId(), l.getUsername(), nomComplet(l), l.getPhone()));
        }
        evenements.save(new EvenementSortant(EvenementSortant.Type.STATUT_CHANGE, c.getId(), maintenant));
        journal.enregistrer(Domaine.COMMANDE, "STATUT_" + cible, String.valueOf(id), message(c, cible));
        commandes.flush();
        return CommandeVue.pourStaff(c, role);
    }

    /**
     * Prise en charge par un livreur d'une commande partie en livraison, sous
     * verrou : si deux livreurs la prennent au meme instant, le second recoit
     * 409 avec le nom de celui qui l'a. Reprendre sa propre commande ne
     * change rien.
     */
    @Transactional
    public CommandeVue prendreEnCharge(long id, String acteur) {
        Commande c = commandes.verrouiller(id)
                .filter(x -> VisibiliteCommandes.voit(Role.LIVREUR, x))
                .orElseThrow(() -> new RessourceIntrouvableException("Commande introuvable."));
        StaffUser moi = personnel.findByUsernameIgnoreCase(acteur)
                .orElseThrow(() -> new RessourceIntrouvableException("Livreur introuvable."));
        if (c.getStatut() != StatutCommande.EN_LIVRAISON) {
            throw new ConflitException("STATUT_INCOMPATIBLE", "Cette commande n'est pas en attente d'un livreur.");
        }
        if (c.getLivreurId() != null) {
            if (c.getLivreurId().equals(moi.getId())) {
                return CommandeVue.pourStaff(c, Role.LIVREUR);
            }
            throw new ConflitException("DEJA_PRISE", "Cette commande a déjà été prise par " + c.getLivreurNom() + ".");
        }
        Instant maintenant = Instant.now(horloge);
        c.attribuerLivreur(moi.getId(), moi.getUsername(), nomComplet(moi), moi.getPhone());
        evenements.save(new EvenementSortant(EvenementSortant.Type.STATUT_CHANGE, c.getId(), maintenant));
        journal.enregistrer(Domaine.COMMANDE, "PRISE_EN_CHARGE", String.valueOf(id),
                "a pris en charge la livraison de la commande #" + c.getId());
        commandes.flush();
        return CommandeVue.pourStaff(c, Role.LIVREUR);
    }

    public static String nomComplet(StaffUser u) {
        String nom = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return nom.isEmpty() ? u.getUsername() : nom;
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
