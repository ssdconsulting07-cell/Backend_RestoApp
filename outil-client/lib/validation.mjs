// Validation du dossier d'un restaurant : schema JSON (champs, formats), puis
// controles que le schema ne peut pas faire (fichiers presents, tailles
// d'image, references croisees, zones, secrets). Toutes les erreurs sont
// collectees d'un coup, en francais, pour tout corriger en une fois.
import { existsSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv from 'ajv';
import ajvErrors from 'ajv-errors';
import ajvFormats from 'ajv-formats';
import sharp from 'sharp';
import { FACULTATIFS, FICHIERS_DONNEES, lireJson } from './lecture.mjs';
import { traduire } from './messages.mjs';

const ici = dirname(fileURLToPath(import.meta.url));
const lireSchema = (nom) => JSON.parse(readFileSync(resolve(ici, '../schema', nom), 'utf8'));
export const REFERENCE = JSON.parse(readFileSync(resolve(ici, '../reference/secteurs-officiels.json'), 'utf8'));

const PHOTO_MAX_OCTETS = 2 * 1024 * 1024; // limite du backend (multipart 2 Mo)
const MOTS_SECRETS = /(mot.?de.?passe|password|passwd|mdp|secret|token|jeton|api.?key|cle.?api|private.?key)/i;

let validateurs;
function compiler() {
  if (validateurs) return validateurs;
  const ajv = new Ajv({ allErrors: true, strict: false });
  ajvFormats(ajv);
  ajvErrors(ajv);
  ajv.addSchema(lireSchema('donnees.schema.json'));
  validateurs = {
    restaurant: ajv.compile(lireSchema('restaurant.schema.json')),
    ...Object.fromEntries(Object.values(FICHIERS_DONNEES).map((def) => [def, ajv.getSchema(`donnees.schema.json#/definitions/${def}`)])),
  };
  return validateurs;
}

/** "Plateau/Gorée" -> "plateau goree" : comparaison sans accents ni casse. */
export const normaliser = (t) => String(t || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
  .replace(/[^a-z0-9]+/g, ' ').trim();

/** Meme regle que Secteur.identifiant() du backend. */
export const idSecteur = (region, nom) => `${region}-${String(nom).normalize('NFD').replace(/\p{M}/gu, '')
  .toUpperCase().replace(/[^A-Z0-9]+/g, '-').replace(/(^-|-$)/g, '')}`;

/** Arrondissement officiel correspondant a une zone du restaurant, ou null. */
export function secteurOfficiel(region, nom) {
  const n = normaliser(nom);
  return REFERENCE.secteurs.find((s) => s.region === region && (normaliser(s.nom) === n || s.id === nom)) || null;
}

/** Arrondissements officiels proches (suggestion en cas de faute de frappe). */
function suggestions(region, nom) {
  const n = normaliser(nom);
  return REFERENCE.secteurs.filter((s) => s.region === region)
    .filter((s) => { const m = normaliser(s.nom); return m.includes(n.slice(0, 4)) || n.includes(m.slice(0, 4)); })
    .slice(0, 3).map((s) => s.nom);
}

/** Cherche les cles qui ressemblent a un secret, partout dans un objet. */
function secrets(objet, chemin = '') {
  if (!objet || typeof objet !== 'object') return [];
  return Object.entries(objet).flatMap(([k, v]) => {
    const ici2 = Array.isArray(objet) ? `${chemin}[${k}]` : `${chemin ? `${chemin} › ` : ''}${k}`;
    return [...(!Array.isArray(objet) && MOTS_SECRETS.test(k) ? [ici2] : []), ...secrets(v, ici2)];
  });
}

async function dimensions(fichier) {
  try {
    const m = await sharp(fichier).metadata();
    return { largeur: m.width, hauteur: m.height, format: m.format };
  } catch {
    return null;
  }
}

const mo = (octets) => `${(octets / 1024 / 1024).toFixed(1).replace('.', ',')} Mo`;

/**
 * Valide le dossier. Renvoie { erreurs, avertissements, restaurant, donnees } ;
 * erreurs et avertissements : [{ fichier, chemin, message }].
 */
export async function valider(dossier) {
  const v = compiler();
  const erreurs = [];
  const avertissements = [];
  const erreur = (fichier, chemin, message) => erreurs.push({ fichier, chemin, message });
  const avertir = (fichier, chemin, message) => avertissements.push({ fichier, chemin, message });

  // --- 1. Schema ------------------------------------------------------------------
  const lu = lireJson(join(dossier, 'restaurant.json'));
  const restaurant = lu.valeur;
  if (lu.erreur) erreur('restaurant.json', '', lu.erreur);
  else if (!v.restaurant(restaurant)) {
    traduire(v.restaurant.errors, restaurant).forEach((e) => erreur('restaurant.json', e.chemin, e.message));
  }
  const donnees = {};
  for (const [fichier, def] of Object.entries(FICHIERS_DONNEES)) {
    const r = lireJson(join(dossier, 'donnees', fichier), { facultatif: FACULTATIFS.has(fichier) });
    donnees[def] = Array.isArray(r.valeur) ? r.valeur : [];
    if (r.erreur) erreur(`donnees/${fichier}`, '', r.erreur);
    else if (!v[def](r.valeur)) traduire(v[def].errors, r.valeur).forEach((e) => erreur(`donnees/${fichier}`, e.chemin, e.message));
  }

  // --- 2. Aucun secret dans le dossier ----------------------------------------------
  if (restaurant) secrets(restaurant).forEach((c) => erreur('restaurant.json', c, 'secret interdit dans le dossier client : passez par .env.secrets au déploiement'));
  for (const [fichier, def] of Object.entries(FICHIERS_DONNEES)) {
    secrets(donnees[def]).forEach((c) => erreur(`donnees/${fichier}`, c,
      'secret interdit dans le dossier client (les mots de passe temporaires sont générés au déploiement)'));
  }
  if (!restaurant || typeof restaurant !== 'object') return { erreurs, avertissements, restaurant, donnees };

  // --- 3. Fichiers et images ------------------------------------------------------------
  const fichierPresent = (fichierJson, chemin, relatif, quoi) => {
    const absolu = join(dossier, relatif);
    if (!existsSync(absolu) || !statSync(absolu).isFile()) {
      erreur(fichierJson, chemin, `${quoi} introuvable : ${relatif}`);
      return null;
    }
    return absolu;
  };
  const a = restaurant.assets || {};
  const verifierImage = async (cle, quoi, { min, carre, exact } = {}) => {
    const rel = typeof cle === 'string' ? a[cle] : cle.chemin;
    const chemin = typeof cle === 'string' ? `assets › ${cle}` : cle.libelle;
    if (typeof rel !== 'string') return;
    const f = fichierPresent('restaurant.json', chemin, rel, quoi);
    if (!f) return;
    const d = await dimensions(f);
    if (!d) return erreur('restaurant.json', chemin, `${quoi} illisible : ${rel} (fichier image corrompu ?)`);
    if (d.format === 'svg') return;
    if (exact && (d.largeur !== exact || d.hauteur !== exact)) {
      return erreur('restaurant.json', chemin, `${quoi} : ${exact} × ${exact} px attendus (${d.largeur} × ${d.hauteur})`);
    }
    if (min && (d.largeur < min[0] || d.hauteur < min[1])) {
      erreur('restaurant.json', chemin, `${quoi} trop petit(e) : ${d.largeur} × ${d.hauteur} px, ${min[0]} × ${min[1]} px minimum (ou SVG)`);
    }
    if (carre && Math.abs(d.largeur / d.hauteur - 1) > 0.1) {
      avertir('restaurant.json', chemin, `${quoi} pas carré(e) (${d.largeur} × ${d.hauteur}) : il sera centré sur un carré`);
    }
  };
  await verifierImage('logo', 'logo', { min: [512, 512], carre: true });
  await verifierImage('monogramme', 'monogramme', { min: [512, 512], carre: true });
  await verifierImage('favicon', 'favicon', { min: [32, 32], carre: true });
  await verifierImage('banniere', 'bannière', { min: [1200, 500] });
  await verifierImage('splash', 'image du splash', { min: [720, 1280] });
  const tailles = { icone192: 192, icone512: 512, maskable512: 512, appleTouch: 180 };
  for (const [cle, px] of Object.entries(a.iconesPwa || {})) {
    await verifierImage({ chemin: a.iconesPwa[cle], libelle: `assets › iconesPwa › ${cle}` }, `icône ${cle}`, { exact: tailles[cle] });
  }
  for (const [i, rel] of (a.connexion || []).entries()) {
    await verifierImage({ chemin: rel, libelle: `assets › connexion[${i}]` }, 'photo de connexion', { min: [1000, 700] });
  }

  const verifierPhoto = async (fichierJson, chemin, rel) => {
    if (typeof rel !== 'string' || rel.startsWith('https://')) return;
    const f = fichierPresent(fichierJson, chemin, rel, 'photo');
    if (!f) return;
    const octets = statSync(f).size;
    if (octets > PHOTO_MAX_OCTETS) erreur(fichierJson, chemin, `photo trop lourde : ${mo(octets)}, 2 Mo maximum (${rel})`);
    const d = await dimensions(f);
    if (!d || d.format === 'svg') erreur(fichierJson, chemin, `photo illisible (JPEG, PNG ou WebP attendu) : ${rel}`);
    else if (d.largeur < 400) avertir(fichierJson, chemin, `photo petite (${d.largeur} px de large, 800 conseillés) : ${rel}`);
  };
  for (const [i, p] of donnees.produits.entries()) await verifierPhoto('donnees/produits.json', `[${i}] « ${p?.id} » › photo`, p?.photo);
  for (const [i, p] of donnees.platsDuJour.entries()) await verifierPhoto('donnees/plats-du-jour.json', `[${i}] « ${p?.id} » › photo`, p?.photo);
  for (const [i, an] of donnees.annonces.entries()) {
    for (const [j, s] of (an?.slides || []).entries()) await verifierPhoto('donnees/annonces.json', `[${i}] « ${an?.id} » › slides[${j}] › image`, s?.image);
  }

  // --- 4. References croisees et doublons --------------------------------------------------
  const doublons = (fichier, liste, cle, quoi) => {
    const vus = new Map();
    liste.forEach((x, i) => {
      const k = typeof x?.[cle] === 'string' ? x[cle].trim().toLowerCase() : null;
      if (!k) return;
      if (vus.has(k)) erreur(fichier, `[${i}] « ${x[cle]} »`, `${quoi} en double (déjà utilisé en [${vus.get(k)}])`);
      else vus.set(k, i);
    });
  };
  doublons('donnees/categories.json', donnees.categories, 'id', 'id');
  doublons('donnees/categories.json', donnees.categories, 'nom', 'nom de catégorie');
  doublons('donnees/produits.json', donnees.produits, 'id', 'id');
  doublons('donnees/accompagnements.json', donnees.accompagnements, 'id', 'id');
  doublons('donnees/plats-du-jour.json', donnees.platsDuJour, 'id', 'id');
  doublons('donnees/annonces.json', donnees.annonces, 'id', 'id');
  doublons('donnees/personnel.json', donnees.personnel, 'identifiant', 'identifiant');

  const idsCategories = new Set(donnees.categories.map((c) => c?.id));
  donnees.produits.forEach((p, i) => {
    if (p?.categorie && !idsCategories.has(p.categorie)) {
      erreur('donnees/produits.json', `[${i}] « ${p.id} » › categorie`,
        `catégorie inconnue « ${p.categorie} » (ids de categories.json : ${[...idsCategories].join(', ')})`);
    }
  });
  const managers = donnees.personnel.filter((m) => m?.role === 'MANAGER');
  if (managers.length > 1) erreur('donnees/personnel.json', '', `un seul compte MANAGER possible (${managers.length} trouvés)`);
  if (!donnees.personnel.some((m) => m?.role === 'GERANT')) {
    avertir('donnees/personnel.json', '', 'aucun compte GERANT : personne ne pourra gérer le menu depuis le Manager');
  }

  // --- 5. Identite, paiements, modes -----------------------------------------------------------
  if (restaurant.nomAccent && typeof restaurant.nom === 'string'
    && !restaurant.nom.toLowerCase().endsWith(restaurant.nomAccent.toLowerCase())) {
    erreur('restaurant.json', 'nomAccent', `« ${restaurant.nomAccent} » doit être la fin du nom « ${restaurant.nom} »`);
  }
  const pay = restaurant.paiements || {};
  const modes = restaurant.modes || {};
  const enLigne = ['wave', 'orangeMoney', 'mixxByYas', 'carte'].some((k) => pay[k] === true);
  if (modes.livraison === false && modes.retraitComptoir === false) {
    erreur('restaurant.json', 'modes', 'au moins un mode : livraison ou retraitComptoir');
  }
  if ((modes.livraison || modes.retraitComptoir) && !enLigne) {
    erreur('restaurant.json', 'paiements', 'aucun moyen de paiement en ligne : l\'App Client a besoin de Wave, Orange Money, Mixx by Yas ou la carte');
  }
  const dep = restaurant.deploiement || {};
  if (dep.urlClient && dep.urlClient === dep.urlManager) {
    erreur('restaurant.json', 'deploiement', 'urlClient et urlManager doivent être deux adresses différentes');
  }

  // --- 6. Zones de livraison ----------------------------------------------------------------------
  const zones = restaurant.livraison?.zones;
  if (modes.livraison === true && !Array.isArray(zones)) {
    erreur('restaurant.json', 'livraison', 'zones de livraison manquantes : la livraison est proposée (modes.livraison)');
  }
  if (modes.livraison === false && Array.isArray(zones)) {
    avertir('restaurant.json', 'livraison', 'zones ignorées : la livraison n\'est pas proposée');
  }
  const codes = new Set(REFERENCE.regions.map((r) => r.code));
  const vues = new Map();
  (Array.isArray(zones) ? zones : []).forEach((z, i) => {
    if (!z || typeof z !== 'object') return;
    const chemin = `livraison › zones[${i}] « ${z.secteur} »`;
    if (z.region && !codes.has(z.region)) {
      return erreur('restaurant.json', chemin, `région inconnue « ${z.region} » (${[...codes].join(', ')})`);
    }
    if (!z.region || !z.secteur) return;
    const officiel = secteurOfficiel(z.region, z.secteur);
    if (!officiel && !z.departement) {
      const proches = suggestions(z.region, z.secteur);
      erreur('restaurant.json', chemin, `« ${z.secteur} » n'est pas un arrondissement officiel de ${z.region} : `
        + `précisez son departement pour en faire une zone du restaurant${proches.length ? ` (vouliez-vous : ${proches.join(', ')} ?)` : ''}`);
    }
    const id = officiel ? officiel.id : idSecteur(z.region, z.secteur);
    if (vues.has(id)) erreur('restaurant.json', chemin, `zone en double (déjà en zones[${vues.get(id)}])`);
    else vues.set(id, i);
  });

  return { erreurs, avertissements, restaurant, donnees };
}

/** Une ligne par probleme : "restaurant.json › couleurs › principale : couleur invalide...". */
export function formater({ fichier, chemin, message }) {
  return `${fichier}${chemin ? ` › ${chemin}` : ''} : ${message}`;
}
