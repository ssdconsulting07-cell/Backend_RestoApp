package sn.chezketchup.backend.commun.publication;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Cycle editorial du back-office (Gerant), commun au catalogue et aux
 * annonces. Seul ACTIF est visible cote client.
 *
 * <pre>
 *   BROUILLON --publier--> ACTIF --archiver--> ARCHIVE
 *       ^  \_____archiver______________________/  |
 *       |____ depublier (ACTIF) / restaurer (ARCHIVE)
 * </pre>
 *
 * Memes actions que les ecrans du Manager (Publier, Depublier, Archiver,
 * Restaurer).
 */
public enum StatutPublication {
    BROUILLON,
    ACTIF,
    ARCHIVE;

    private static final Map<StatutPublication, Set<StatutPublication>> SUIVANTS = Map.of(
            BROUILLON, EnumSet.of(ACTIF, ARCHIVE),
            ACTIF, EnumSet.of(BROUILLON, ARCHIVE),
            ARCHIVE, EnumSet.of(BROUILLON));

    /** Rester dans le meme statut est toujours permis (requete rejouee). */
    public boolean peutPasserA(StatutPublication cible) {
        return this == cible || SUIVANTS.get(this).contains(cible);
    }
}
