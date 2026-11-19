#!/usr/bin/env node
// Personnalise SenYummies pour un restaurant, a partir de SON dossier.
//
//   npm run client -- <chemin-ou-identifiant> [options]
//
//   <chemin-ou-identifiant>  dossier du restaurant (ex. ../Clients_RestoApp/chez-fatou),
//                            ou identifiant cherche dans $CLIENTS_DIR puis clients/
//   --verifier               valide seulement, ne genere rien
//   --forcer                 au prochain demarrage, reimpose le catalogue du dossier
//                            (ecrase les modifications du Gerant ; comptes jamais touches)
//   --dev                    ecrit aussi dev/.env.local (VITE_MARQUE_*) pour npm run dev
//   --sortie <dossier>       ou generer (defaut : deploiements/<identifiant>/ de ce depot)
//
// Voir PERSONNALISER-UN-RESTAURANT.md.
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { generer } from './lib/generation.mjs';
import { resoudreDossier } from './lib/lecture.mjs';
import { formater, valider } from './lib/validation.mjs';

const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '..');

async function principal(argv) {
  const { values: options, positionals } = parseArgs({
    args: argv,
    allowPositionals: true,
    options: {
      verifier: { type: 'boolean', default: false },
      forcer: { type: 'boolean', default: false },
      dev: { type: 'boolean', default: false },
      sortie: { type: 'string' },
      aide: { type: 'boolean', short: 'h', default: false },
    },
  });
  if (options.aide || positionals.length !== 1) {
    console.log('Usage : npm run client -- <chemin-ou-identifiant> [--verifier] [--forcer] [--dev] [--sortie <dossier>]');
    return options.aide ? 0 : 2;
  }

  const dossier = resoudreDossier(positionals[0], { racine: RACINE });
  console.log(`Restaurant : ${dossier}`);
  const validation = await valider(dossier);
  for (const a of validation.avertissements) console.log(`  ⚠ ${formater(a)}`);
  if (validation.erreurs.length) {
    console.error(`\n✖ ${validation.erreurs.length} erreur(s) à corriger :`);
    validation.erreurs.forEach((e, i) => console.error(`  ${i + 1}. ${formater(e)}`));
    return 1;
  }
  console.log('✔ Configuration valide');
  if (options.verifier) return 0;

  const sortie = resolve(options.sortie || join(RACINE, 'deploiements', validation.restaurant.identifiant));
  const r = await generer({ validation, dossier, sortie, forcer: options.forcer, dev: options.dev });
  const c = r.compte;
  console.log(`✔ Généré dans ${relative(process.cwd(), sortie) || '.'}`);
  console.log(`  marque/ : ${r.fichiersMarque} fichier(s) · photos : ${r.photos}`);
  console.log(`  catalogue : ${c.categories} catégories, ${c.produits} produits, ${c.accompagnements} accompagnements, `
    + `${c.platsDuJour} plats du jour, ${c.annonces} annonces, ${c.zones} zones, ${c.personnel} comptes`);
  if (c.personnel) console.log('  comptes-initiaux.txt : mots de passe temporaires (CONFIDENTIEL, jamais commité)');
  if (options.forcer) console.log('  ⚠ --forcer : au prochain démarrage, les fiches du dossier remplaceront celles du Gerant');
  console.log(`\nSuite : voir ${relative(process.cwd(), join(sortie, 'LISEZMOI.md'))}`);
  return 0;
}

principal(process.argv.slice(2)).then((code) => { process.exitCode = code; }, (e) => {
  console.error(`✖ ${e.message}`);
  process.exitCode = 1;
});
