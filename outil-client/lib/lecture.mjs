// Lecture du dossier d'un restaurant : ou il est, et ses fichiers JSON.
import { existsSync, readFileSync, statSync } from 'node:fs';
import { isAbsolute, join, resolve } from 'node:path';

/** Fichiers de donnees/ : nom du fichier -> nom du schema (donnees.schema.json#/definitions/...). */
export const FICHIERS_DONNEES = {
  'categories.json': 'categories',
  'produits.json': 'produits',
  'accompagnements.json': 'accompagnements',
  'plats-du-jour.json': 'platsDuJour',
  'annonces.json': 'annonces',
  'personnel.json': 'personnel',
};

/** Fichiers facultatifs (liste vide si absents). */
export const FACULTATIFS = new Set(['accompagnements.json', 'plats-du-jour.json', 'annonces.json', 'personnel.json']);

const estDossier = (p) => existsSync(p) && statSync(p).isDirectory();

/**
 * <chemin-ou-id> -> dossier du restaurant. Dans l'ordre :
 *   1. un chemin vers un dossier existant (ex. ../Clients_RestoApp/chez-fatou) ;
 *   2. <CLIENTS_DIR>/<id> si la variable CLIENTS_DIR est definie (depot prive) ;
 *   3. clients/<id> dans ce depot (clients/exemple).
 */
export function resoudreDossier(argument, { racine, cwd = process.cwd(), env = process.env } = {}) {
  if (!argument) {
    throw new Error('Indiquez le restaurant : npm run client -- <chemin-ou-identifiant>');
  }
  const essais = [];
  const direct = isAbsolute(argument) ? argument : resolve(cwd, argument);
  essais.push(direct);
  if (estDossier(direct) && existsSync(join(direct, 'restaurant.json'))) return direct;
  if (env.CLIENTS_DIR) {
    const prive = resolve(cwd, env.CLIENTS_DIR, argument);
    essais.push(prive);
    if (estDossier(prive)) return prive;
  }
  const local = join(racine, 'clients', argument);
  essais.push(local);
  if (estDossier(local)) return local;
  throw new Error(`Dossier du restaurant introuvable pour « ${argument} ». Chemins essayés :\n  - ${essais.join('\n  - ')}`);
}

/** Lit un JSON ; erreur lisible (ligne, colonne) si la syntaxe est fausse. */
export function lireJson(fichier, { facultatif = false } = {}) {
  if (!existsSync(fichier)) {
    if (facultatif) return { absent: true, valeur: [] };
    return { erreur: 'fichier manquant' };
  }
  const texte = readFileSync(fichier, 'utf8').replace(/^﻿/, '');
  try {
    return { valeur: JSON.parse(texte) };
  } catch (e) {
    const pos = Number((/position (\d+)/.exec(e.message) || [])[1]);
    let ou = '';
    if (Number.isInteger(pos)) {
      const avant = texte.slice(0, pos).split('\n');
      ou = ` (ligne ${avant.length}, colonne ${avant.at(-1).length + 1})`;
    }
    return { erreur: `JSON invalide${ou} : virgule, guillemet ou accolade en trop ou manquant` };
  }
}
