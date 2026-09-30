/**
 * Fonctionnalite Authentification : endpoint de connexion (POST /auth/login),
 * verification des identifiants et emission du JWT. S'appuie sur
 * l'infrastructure JWT du paquet security/ (Role, JwtService,
 * JwtAuthenticationFilter) sans la dupliquer.
 */
package sn.chezketchup.backend.auth;
