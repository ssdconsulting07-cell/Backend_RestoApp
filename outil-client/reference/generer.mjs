// Regenere reference/secteurs-officiels.json depuis la migration V6 (liste
// officielle des regions et arrondissements du Senegal) : l'outil valide les
// zones de livraison d'un restaurant contre CETTE liste, sans base de donnees.
//
//   node outil-client/reference/generer.mjs
//
// Un test (tests/reference.test.mjs) verifie que le fichier reste aligne sur V6.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ici = dirname(fileURLToPath(import.meta.url));
export const MIGRATION_V6 = resolve(ici, '../../src/main/resources/db/migration/V6__zones_officielles.sql');
export const FICHIER = resolve(ici, 'secteurs-officiels.json');

const chaine = "'((?:[^']|'')*)'";
const nombre = '(-?[0-9.]+|NULL)';

/** Lit V6 : { regions: [{ code, nom }], secteurs: [{ id, region, departement, nom }] }. */
export function lireV6(sql = readFileSync(MIGRATION_V6, 'utf8')) {
  const texte = (s) => s.replace(/''/g, "'");
  const regions = [...sql.matchAll(new RegExp(String.raw`\(${chaine}, ${chaine}, b'[01]', 0, ${chaine}\)`, 'g'))]
    .map((m) => ({ code: texte(m[1]), nom: texte(m[2]) }));
  const secteurs = [...sql.matchAll(new RegExp(String.raw`\(${chaine}, ${chaine}, ${chaine}, ${chaine}, \d+, ${nombre}, ${nombre}, b'1'`, 'g'))]
    .map((m) => ({ id: texte(m[1]), region: texte(m[2]), departement: texte(m[3]), nom: texte(m[4]) }));
  return { regions, secteurs };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const ref = lireV6();
  writeFileSync(FICHIER, `${JSON.stringify(ref, null, 2)}\n`);
  console.log(`${ref.regions.length} regions, ${ref.secteurs.length} arrondissements -> ${FICHIER}`);
}
