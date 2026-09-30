/**
 * Sonde de sante minimale (GET /health, sans authentification, sans acces
 * base de donnees) : permet aux frontends (App Client, SenYummies Manager)
 * de detecter une coupure reseau sans solliciter un endpoint metier a
 * chaque verification (voir NetworkStatus.jsx cote SenYummies Manager).
 */
package sn.chezketchup.backend.health;
