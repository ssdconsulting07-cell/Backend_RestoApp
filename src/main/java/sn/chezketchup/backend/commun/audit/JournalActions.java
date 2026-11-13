package sn.chezketchup.backend.commun.audit;

/**
 * Port du journal d'audit : chaque action d'ecriture y est consignee, dans
 * la meme transaction que l'action (atomicite). Implementation :
 * {@code audit.infrastructure.JournalActionsBase} (table audit_evenements).
 *
 * Le message est la phrase lisible affichee dans le Journal d'audit du
 * Manager (« a publie le produit Thieboudienne »). Jamais de donnee
 * personnelle de client (nom, telephone, position) dans un message.
 */
public interface JournalActions {

    /** Action de l'utilisateur courant (membre du staff, ou "client" / "systeme" s'il n'y en a pas). */
    void enregistrer(Domaine domaine, String action, String cibleId, String message);

    /** Action d'un acteur non humain explicite : "systeme" (taches planifiees), "prestataire" (webhook). */
    void enregistrerEnTantQue(String acteur, Domaine domaine, String action, String cibleId, String message);

    enum Domaine {
        COMMANDE, PRODUIT, CATEGORIE, ANNONCE, PERSONNEL, ZONE, PAIEMENT
    }
}
