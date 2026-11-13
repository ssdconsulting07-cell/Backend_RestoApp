package sn.chezketchup.backend.audit.infrastructure;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import sn.chezketchup.backend.audit.domaine.AuditEvenement;
import sn.chezketchup.backend.auth.StaffUser;
import sn.chezketchup.backend.auth.StaffUserRepository;
import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.securite.UtilisateurCourant;
import sn.chezketchup.backend.security.Role;

/**
 * Journal d'audit en base. REQUIRED : rejoint la transaction de l'action
 * (l'entree n'existe que si l'action est validee) ; en ouvre une s'il n'y en
 * a pas (ex. depot de photo).
 */
@Component
@Transactional(propagation = Propagation.REQUIRED)
public class JournalActionsBase implements JournalActions {

    private final AuditEvenementRepository journal;
    private final StaffUserRepository staff;
    private final Clock horloge;

    public JournalActionsBase(AuditEvenementRepository journal, StaffUserRepository staff, Clock horloge) {
        this.journal = journal;
        this.staff = staff;
        this.horloge = horloge;
    }

    @Override
    public void enregistrer(Domaine domaine, String action, String cibleId, String message) {
        String identifiant = UtilisateurCourant.identifiant().orElse(null);
        if (identifiant == null) {
            // Pas de membre du staff : requete de l'App Client, sinon tache du systeme.
            enregistrerEnTantQue(RequestContextHolder.getRequestAttributes() != null ? "client" : "systeme",
                    domaine, action, cibleId, message);
            return;
        }
        String role = UtilisateurCourant.role().map(Role::name).orElse(null);
        String nom = staff.findByUsernameIgnoreCase(identifiant).map(JournalActionsBase::nomAffiche).orElse(identifiant);
        journal.save(new AuditEvenement(Instant.now(horloge), identifiant, nom, role, domaine.name(), action, cibleId, message));
    }

    @Override
    public void enregistrerEnTantQue(String acteur, Domaine domaine, String action, String cibleId, String message) {
        String nom = switch (acteur) {
            case "client" -> "Client (App)";
            case "prestataire" -> "Prestataire de paiement";
            default -> "Système";
        };
        journal.save(new AuditEvenement(Instant.now(horloge), acteur, nom, null, domaine.name(), action, cibleId, message));
    }

    static String nomAffiche(StaffUser u) {
        String nom = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return nom.isEmpty() ? u.getUsername() : nom;
    }
}
