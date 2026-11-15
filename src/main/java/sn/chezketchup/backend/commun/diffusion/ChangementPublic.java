package sn.chezketchup.backend.commun.diffusion;

/**
 * Donnees publiques modifiees (catalogue, plats du jour, annonces,
 * horaires). Publie par les services d'ecriture ; relaye APRES validation de
 * la transaction vers les App Client connectees (tempsreel.FluxPublic), qui
 * rechargent alors le sujet concerne.
 */
public record ChangementPublic(Sujet sujet) {

    public enum Sujet {
        CATALOGUE("catalogue"),
        PLATS_DU_JOUR("plats-du-jour"),
        ANNONCES("annonces"),
        HORAIRES("horaires"),
        ZONES("zones");

        private final String nom;

        Sujet(String nom) {
            this.nom = nom;
        }

        public String nom() {
            return nom;
        }
    }
}
