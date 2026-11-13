package sn.chezketchup.backend.commandes.domaine;

/** Petits types du domaine des commandes (meme vocabulaire que le contrat et les fronts). */
public final class Enums {

    private Enums() {
    }

    /** APP = App Client (invite, sans compte) ; COMPTOIR = saisie par le Vendeur. */
    public enum Canal { APP, COMPTOIR }

    public enum ModeCommande { LIVRAISON, RETRAIT }

    public enum TypeLigne { PRODUIT, PLAT_DU_JOUR }

    public enum StatutCommande {
        CREEE, PAYEE, EN_PREPARATION, PRETE, EN_LIVRAISON, LIVREE, RETIREE, ANNULEE;

        public boolean estTerminal() {
            return this == LIVREE || this == RETIREE || this == ANNULEE;
        }
    }
}
