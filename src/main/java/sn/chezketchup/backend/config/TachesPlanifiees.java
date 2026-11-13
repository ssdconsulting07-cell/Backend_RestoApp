package sn.chezketchup.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Taches planifiees : relais de l'outbox temps reel (chaque seconde), signal
 * de vie SSE, purge des cles d'idempotence, nettoyage de la limitation de
 * debit. Actives par defaut ; desactivables (app.taches-planifiees.actives=false)
 * pour les tests qui n'en ont pas besoin.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.taches-planifiees.actives", havingValue = "true", matchIfMissing = true)
public class TachesPlanifiees {
}
