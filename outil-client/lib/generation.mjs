// Generation de deploiements/<restaurant>/ : tout ce qu'il faut pour deployer
// le restaurant avec les images Docker COMMUNES, sans aucun secret.
//
//   .env                    marque, domaines, offre (regenere a chaque fois)
//   .env.secrets.exemple    secrets a fournir (copier en .env.secrets, jamais commite)
//   docker-compose.yml      production (HTTPS Caddy, MySQL, sauvegardes)
//   docker-compose.local.yml  essai sur le poste (http://localhost:8081 / 8082)
//   Caddyfile
//   marque/                 logo, bannieres, icones (monte en /marque dans les apps)
//   donnees/                import.json + photos (monte en /restaurant dans le backend)
//   comptes-initiaux.txt    mots de passe temporaires (CONFIDENTIEL, jamais commite)
//   dev/                    avec --dev : .env.local des deux apps (npm run dev)
import { copyFileSync, existsSync, mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { lireComptesExistants, preparerComptes } from './comptes.mjs';
import { preparerMarque } from './images.mjs';
import { construireImport, nouveauJeton } from './import.mjs';
import { fichierEnv, variables, variablesVite } from './marque.mjs';

const ici = dirname(fileURLToPath(import.meta.url));
const MODELES = resolve(ici, '../modeles');
const CADDYFILE = resolve(ici, '../../deploiement/prod/Caddyfile');

const SECRETS_EXEMPLE = (r) => `# Secrets du restaurant ${r.nom} — à copier en .env.secrets puis remplir.
# .env.secrets ne doit JAMAIS être commité ni envoyé par e-mail.
#
#   docker compose --env-file .env --env-file .env.secrets up -d

# Base de données (mots de passe longs et aléatoires : openssl rand -base64 32)
MYSQL_ROOT_PASSWORD=
MYSQL_PASSWORD=

# Signature des sessions du personnel : 32 caractères minimum (openssl rand -base64 48)
JWT_SECRET=

# Prestataire de paiement réel en production (le backend refuse « simule » en prod).
# Pour l'essai local (docker-compose.local.yml) : PAIEMENT_PRESTATAIRE=simule
PAIEMENT_PRESTATAIRE=
# Clés du prestataire (noms définis avec son intégration), par exemple :
# PAIEMENT_CLE_PUBLIQUE=
# PAIEMENT_CLE_PRIVEE=
# PAIEMENT_SECRET_WEBHOOK=

# Premier compte Manager, UNIQUEMENT si donnees/personnel.json n'en contient pas.
# Usage unique : renseigner, démarrer, puis vider ces deux lignes.
MANAGER_INITIAL_IDENTIFIANT=
MANAGER_INITIAL_MOT_DE_PASSE=
`;

const LISEZMOI = (r, vars, avecComptes) => `# Déploiement — ${r.nom}

Généré par \`npm run client -- ${r.identifiant}\`. Ne pas modifier à la main :
modifier le dossier du restaurant, puis relancer la commande.

## Essai sur le poste

    cp .env.secrets.exemple .env.secrets   # puis remplir (PAIEMENT_PRESTATAIRE=simule)
    docker compose --env-file .env --env-file .env.secrets -f docker-compose.yml -f docker-compose.local.yml up -d

- App Client : http://localhost:8081
- Manager : http://localhost:8082${avecComptes ? ' (identifiants : comptes-initiaux.txt)' : ''}

## Mise en ligne

1. DNS : ${vars.DOMAINE_CLIENT} et ${vars.DOMAINE_MANAGER} pointent vers le serveur (ports 80 et 443 ouverts).
2. Copier ce dossier sur le serveur, SAUF comptes-initiaux.txt.
3. Créer .env.secrets (prestataire de paiement réel), puis :

       docker compose --env-file .env --env-file .env.secrets up -d
       docker compose logs -f backend    # « Import du restaurant ... » puis « Started »

Images communes : ${vars.REGISTRE || 'senyummies'}/{backend,client,manager}:${vars.VERSION}.
`;

/**
 * Ecrit deploiements/<id>/. `validation` = resultat de valider() sans erreur.
 * Renvoie un resume pour l'affichage.
 */
export async function generer({ validation, dossier, sortie, forcer = false, dev = false, maintenant = new Date() }) {
  const { restaurant, donnees } = validation;
  mkdirSync(sortie, { recursive: true });
  // marque/ et donnees/ repartent de zero (une image retiree ne doit pas survivre) ;
  // .env.secrets et comptes-initiaux.txt sont conserves.
  for (const sousDossier of ['marque', 'donnees', 'dev']) rmSync(join(sortie, sousDossier), { recursive: true, force: true });
  mkdirSync(join(sortie, 'marque'), { recursive: true });
  mkdirSync(join(sortie, 'donnees'), { recursive: true });

  const vars = variables(restaurant);
  const fichiersMarque = await preparerMarque(restaurant, dossier, join(sortie, 'marque'));

  const fichierComptes = join(sortie, 'comptes-initiaux.txt');
  const comptes = preparerComptes(donnees.personnel, restaurant, lireComptesExistants(fichierComptes), maintenant);
  if (donnees.personnel.length) writeFileSync(fichierComptes, comptes.texte, { mode: 0o600 });

  const jeton = nouveauJeton(maintenant);
  const imp = construireImport({
    restaurant, donnees, membres: comptes.membres, dossier, sortieDonnees: join(sortie, 'donnees'), forcer, jeton,
  });
  writeFileSync(join(sortie, 'donnees', 'import.json'), `${JSON.stringify(imp, null, 2)}\n`);

  writeFileSync(join(sortie, '.env'), fichierEnv(vars,
    `# ${restaurant.nom} — généré par npm run client le ${maintenant.toISOString()}.\n`
    + '# Aucun secret ici : ils vont dans .env.secrets (voir .env.secrets.exemple).'));
  writeFileSync(join(sortie, '.env.secrets.exemple'), SECRETS_EXEMPLE(restaurant));
  writeFileSync(join(sortie, '.gitignore'), '# Jamais dans git : secrets et mots de passe temporaires.\n.env.secrets\ncomptes-initiaux.txt\n');
  copyFileSync(join(MODELES, 'docker-compose.yml'), join(sortie, 'docker-compose.yml'));
  copyFileSync(join(MODELES, 'docker-compose.local.yml'), join(sortie, 'docker-compose.local.yml'));
  copyFileSync(CADDYFILE, join(sortie, 'Caddyfile'));
  writeFileSync(join(sortie, 'LISEZMOI.md'), LISEZMOI(restaurant, vars, donnees.personnel.length > 0));

  if (dev) {
    mkdirSync(join(sortie, 'dev'), { recursive: true });
    const entete = `# ${restaurant.nom} en développement : copier en .env.local dans Client_RestoApp et Manager_RestoApp,\n`
      + '# et copier le dossier marque/ dans public/marque/ de chaque app (ignoré par git).';
    writeFileSync(join(sortie, 'dev', '.env.local'), fichierEnv(variablesVite(vars), entete));
  }

  return {
    sortie,
    jeton,
    forcer,
    fichiersMarque: fichiersMarque.length,
    photos: existsSync(join(sortie, 'donnees', 'photos')) ? readdirSync(join(sortie, 'donnees', 'photos')).length : 0,
    compte: {
      categories: imp.categories.length,
      produits: imp.produits.length,
      accompagnements: imp.accompagnements.length,
      platsDuJour: imp.platsDuJour.length,
      annonces: imp.annonces.length,
      zones: imp.zones.reduce((n, z) => n + z.secteurs.length, 0),
      personnel: imp.personnel.length,
    },
  };
}
