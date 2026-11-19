// Generation de deploiements/<restaurant>/ : marque, import.json, comptes,
// fichiers Docker ; aucun secret ; relancer ne change pas les mots de passe deja remis.
import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, describe, it } from 'node:test';
import bcrypt from 'bcryptjs';
import sharp from 'sharp';
import { generer } from '../lib/generation.mjs';
import { resoudreDossier } from '../lib/lecture.mjs';
import { valeurEnv, variables } from '../lib/marque.mjs';
import { valider } from '../lib/validation.mjs';
import { copieExemple, EXEMPLE, nettoyer, RACINE } from './aide.mjs';

after(nettoyer);

async function genererExemple(options = {}) {
  const c = copieExemple();
  const sortie = options.sortie || join(c.base, 'sortie');
  const validation = await valider(c.dossier);
  assert.deepEqual(validation.erreurs, []);
  const resume = await generer({ validation, dossier: c.dossier, sortie, ...options });
  const lire = (f) => readFileSync(join(sortie, f), 'utf8');
  return { c, sortie, resume, lire, imp: JSON.parse(lire('donnees/import.json')) };
}

describe('generation', () => {
  it('ecrit tous les fichiers du deploiement', async () => {
    const { sortie } = await genererExemple();
    for (const f of ['.env', '.env.secrets.exemple', '.gitignore', 'docker-compose.yml', 'docker-compose.local.yml',
      'Caddyfile', 'LISEZMOI.md', 'comptes-initiaux.txt', 'donnees/import.json', 'marque/logo.svg',
      'marque/favicon.svg', 'marque/banniere.jpg', 'marque/splash.jpg', 'marque/connexion-1.jpg']) {
      assert.ok(existsSync(join(sortie, f)), `${f} manquant`);
    }
  });

  it('.env : marque, offre et domaines, aucun secret', async () => {
    const { lire } = await genererExemple();
    const env = lire('.env');
    assert.match(env, /^MARQUE_NOM='Kër Saveurs'$/m);
    assert.match(env, /^MARQUE_COULEUR='#1F7A4D'$/m);
    assert.match(env, /^MARQUE_LOGO_URL='\/marque\/logo.svg'$/m);
    assert.match(env, /^MARQUE_PAIEMENTS='wave,orange-money,especes'$/m);
    assert.match(env, /^APP_RESTAURANT_MOYENS_PAIEMENT='wave,orange-money,especes'$/m);
    assert.match(env, /^DOMAINE_CLIENT='commande.kersaveurs.example'$/m);
    assert.doesNotMatch(env, /PASSWORD|JWT_SECRET|MOT_DE_PASSE|\$2[aby]\$/);
    assert.match(lire('.gitignore'), /comptes-initiaux\.txt/);
  });

  it('icones PWA aux bonnes tailles', async () => {
    const { sortie } = await genererExemple();
    for (const [f, t] of [['icon-192.png', 192], ['icon-512.png', 512], ['maskable-512.png', 512], ['apple-touch-icon.png', 180]]) {
      const m = await sharp(join(sortie, 'marque/icons', f)).metadata();
      assert.deepEqual([m.width, m.height], [t, t], f);
    }
  });

  it('import.json : format 1, fiches avec cle stable, photos copiees, zones officielles reconnues', async () => {
    const { imp, sortie } = await genererExemple();
    assert.equal(imp.format, 1);
    assert.equal(imp.forcer, false);
    assert.equal(imp.categories.length, 5);
    const poisson = imp.produits.find((p) => p.cle === 'poisson-braise');
    assert.equal(poisson.tendance, 'OUI');
    assert.match(poisson.photo, /^photos\/\d{3}-poisson-braise\.jpg$/);
    assert.ok(existsSync(join(sortie, 'donnees', poisson.photo)));
    assert.equal(imp.produits.find((p) => p.cle === 'mafe').disponibilite, 'RUPTURE');
    assert.equal(imp.produits.find((p) => p.cle === 'beignets').statut, 'BROUILLON');
    assert.deepEqual(imp.platsDuJour.find((p) => p.cle === 'thieboudienne').jours, ['LUNDI', 'MERCREDI', 'SAMEDI']);
    assert.equal(imp.platsDuJour.find((p) => p.cle === 'thieboudienne').diner, false);
    const thies = imp.zones.find((z) => z.code === 'THIES');
    assert.equal(thies.masquerAutres, true);
    assert.deepEqual(thies.secteurs[0], { officiel: 'THIES-THIES-NORD', departement: null, nom: null, frais: 500 });
    assert.deepEqual(thies.secteurs.at(-1), { officiel: null, departement: 'Thiès', nom: 'Cité Malick Sy', frais: 700 });
    assert.deepEqual(imp.horaires, [
      { service: 'DEJEUNER', debut: '11:30', fin: '15:00' },
      { service: 'DINER', debut: '18:30', fin: '23:30' },
    ]);
  });

  it('comptes : empreinte BCrypt seulement, mot de passe en clair dans comptes-initiaux.txt seulement', async () => {
    const { imp, lire } = await genererExemple();
    const fatou = imp.personnel.find((m) => m.identifiant === 'fatou.gerant');
    assert.match(fatou.motDePasseHash, /^\$2[aby]\$10\$/);
    assert.equal(fatou.motDePasse, undefined);
    const ligne = lire('comptes-initiaux.txt').split('\n').find((l) => l.startsWith('fatou.gerant\t'));
    const mdp = ligne.split('\t')[3];
    assert.ok(mdp.length >= 14);
    assert.ok(bcrypt.compareSync(mdp, fatou.motDePasseHash));
    assert.ok(!JSON.stringify(imp).includes(mdp));
  });

  it('relancer : nouveau jeton, memes mots de passe, --forcer transmis', async () => {
    const c = copieExemple();
    const sortie = join(c.base, 'sortie');
    const validation = await valider(c.dossier);
    await generer({ validation, dossier: c.dossier, sortie });
    const avant = readFileSync(join(sortie, 'comptes-initiaux.txt'), 'utf8').split('\n').filter((l) => l.includes('\t'));
    const jeton1 = JSON.parse(readFileSync(join(sortie, 'donnees/import.json'), 'utf8')).jeton;
    await generer({ validation, dossier: c.dossier, sortie, forcer: true });
    const apres = readFileSync(join(sortie, 'comptes-initiaux.txt'), 'utf8').split('\n').filter((l) => l.includes('\t'));
    const imp = JSON.parse(readFileSync(join(sortie, 'donnees/import.json'), 'utf8'));
    assert.deepEqual(apres, avant);
    assert.notEqual(imp.jeton, jeton1);
    assert.equal(imp.forcer, true);
  });

  it('--dev : variables VITE_ pour npm run dev', async () => {
    const { lire } = await genererExemple({ dev: true });
    assert.match(lire('dev/.env.local'), /^VITE_MARQUE_NOM='Kër Saveurs'$/m);
    assert.doesNotMatch(lire('dev/.env.local'), /DOMAINE_CLIENT|APP_RESTAURANT/);
  });
});

describe('dossier du restaurant : chemin ou identifiant', () => {
  it('chemin direct (depot prive), CLIENTS_DIR, puis clients/<id>', () => {
    assert.equal(resoudreDossier(EXEMPLE, { racine: RACINE }), EXEMPLE);
    assert.equal(resoudreDossier('exemple', { racine: RACINE, env: {} }), EXEMPLE);
    const prive = mkdtempSync(join(tmpdir(), 'clients-prive-'));
    const c = copieExemple();
    assert.equal(resoudreDossier('exemple', { racine: RACINE, env: { CLIENTS_DIR: c.base } }), c.dossier);
    assert.throws(() => resoudreDossier('inconnu', { racine: RACINE, env: { CLIENTS_DIR: prive } }),
      /introuvable pour « inconnu »/);
  });
});

describe('valeurs du .env', () => {
  it('apostrophes, guillemets et $ restent litteraux pour docker compose', () => {
    assert.equal(valeurEnv('#1F7A4D'), "'#1F7A4D'");
    assert.equal(valeurEnv("L'Atelier"), '"L\'Atelier"');
    assert.equal(valeurEnv('L\'"Atelier" $5'), '"L\'\\"Atelier\\" $$5"');
  });

  it('variables() sans restaurant optionnel : chaines vides, pas undefined', () => {
    const v = variables({ identifiant: 'x', nom: 'X', couleurs: { principale: '#000000' }, assets: { logo: 'a.svg' },
      paiements: { carte: true }, modes: { retraitComptoir: true } });
    assert.equal(v.MARQUE_BANNIERE_URL, '');
    assert.equal(v.MARQUE_MODES, 'retrait');
    assert.ok(Object.values(v).every((x) => typeof x === 'string'));
  });
});
