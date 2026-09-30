package sn.chezketchup.backend.security;

/**
 * Roles internes du staff SenYummies Manager. Un utilisateur de l'App Client
 * public (commande invite) n'a pas de role : il n'est jamais authentifie.
 */
public enum Role {
    CUISINE,
    GERANT,
    MANAGER,
    LIVREUR
}
