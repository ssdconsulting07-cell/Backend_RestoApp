package sn.chezketchup.backend.commun.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import sn.chezketchup.backend.commun.securite.UtilisateurCourant;

/** Implementation provisoire (lot 2) : remplacee par la table d'audit au lot 5. */
@Component
public class JournalActionsLogs implements JournalActions {

    private static final Logger LOG = LoggerFactory.getLogger("audit");

    @Override
    public void enregistrer(Domaine domaine, String action, String cibleId, String message) {
        LOG.info("{} {} {} #{} : {}", UtilisateurCourant.identifiant().orElse("systeme"),
                domaine, action, cibleId, message);
    }
}
