package sn.chezketchup.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.security.Role;

/**
 * Premier compte MANAGER d'une base de production (ou aucun compte n'existe :
 * le seed de demonstration y est interdit et l'API refuse de creer un MANAGER).
 *
 * Usage unique : renseigner APP_AUTH_MANAGER_INITIAL_IDENTIFIANT et
 * APP_AUTH_MANAGER_INITIAL_MOT_DE_PASSE, demarrer, puis RETIRER les deux
 * variables. Le compte est cree avec un mot de passe temporaire a changer a la
 * premiere connexion. S'il existe deja un MANAGER, rien n'est fait.
 */
@Component
@Order(1)
public class ManagerInitial implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ManagerInitial.class);
    static final int LONGUEUR_MIN = 12;

    private final StaffUserRepository comptes;
    private final PasswordEncoder encodeur;
    private final JournalActions journal;
    private final String identifiant;
    private final String motDePasse;

    public ManagerInitial(StaffUserRepository comptes, PasswordEncoder encodeur, JournalActions journal,
                          @Value("${app.auth.manager-initial.identifiant:}") String identifiant,
                          @Value("${app.auth.manager-initial.mot-de-passe:}") String motDePasse) {
        this.comptes = comptes;
        this.encodeur = encodeur;
        this.journal = journal;
        this.identifiant = identifiant == null ? "" : identifiant.trim();
        this.motDePasse = motDePasse == null ? "" : motDePasse;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean avecIdentifiant = !identifiant.isEmpty();
        boolean avecMotDePasse = !motDePasse.isBlank();
        if (!avecIdentifiant && !avecMotDePasse) {
            return;
        }
        if (avecIdentifiant != avecMotDePasse) {
            throw new IllegalStateException("APP_AUTH_MANAGER_INITIAL_IDENTIFIANT et "
                    + "APP_AUTH_MANAGER_INITIAL_MOT_DE_PASSE doivent etre renseignes ensemble");
        }
        if (motDePasse.length() < LONGUEUR_MIN) {
            throw new IllegalStateException("APP_AUTH_MANAGER_INITIAL_MOT_DE_PASSE : " + LONGUEUR_MIN
                    + " caracteres minimum");
        }
        if (comptes.existsByRole(Role.MANAGER)) {
            LOG.warn("Un compte MANAGER existe deja : APP_AUTH_MANAGER_INITIAL_* ignorees, retirez-les.");
            return;
        }
        if (comptes.findByUsernameIgnoreCase(identifiant).isPresent()) {
            throw new IllegalStateException("APP_AUTH_MANAGER_INITIAL_IDENTIFIANT : identifiant deja utilise");
        }
        StaffUser manager = comptes.save(new StaffUser(identifiant, encodeur.encode(motDePasse), Role.MANAGER, true));
        journal.enregistrerEnTantQue("systeme", JournalActions.Domaine.PERSONNEL, "CREATION",
                String.valueOf(manager.getId()), "a cree le premier compte Manager (mot de passe a changer a la premiere connexion)");
        LOG.info("Premier compte MANAGER cree ({}). Retirez maintenant APP_AUTH_MANAGER_INITIAL_*.", identifiant);
    }
}
