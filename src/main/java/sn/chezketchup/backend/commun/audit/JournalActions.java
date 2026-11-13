package sn.chezketchup.backend.commun.audit;

/**
 * Port du journal d'audit : chaque action d'ecriture du staff y est
 * consignee, dans la meme transaction que l'action (atomicite).
 *
 * Lot 2 : implementation provisoire qui journalise dans les logs
 * ({@link JournalActionsLogs}). Lot 5 : implementation en base (AuditEvent),
 * sans toucher aux services qui l'appellent.
 *
 * Le message est la phrase lisible affichee dans le Journal d'audit du
 * Manager (« a publie le produit Thieboudienne »). Jamais de donnee
 * personnelle de client (nom, telephone, position) dans un message.
 */
public interface JournalActions {

    void enregistrer(Domaine domaine, String action, String cibleId, String message);

    enum Domaine {
        COMMANDE, PRODUIT, CATEGORIE, ANNONCE, PERSONNEL, ZONE, PAIEMENT
    }
}
