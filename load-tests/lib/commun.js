// Outils communs aux scenarios de charge SenYummies.
//
// - BASE_URL et liste blanche d'hotes (refus de toute autre cible) ;
// - connexion du staff (mots de passe UNIQUEMENT en variables d'environnement) ;
// - lecture du catalogue, telephones +221, calcul du total attendu ;
// - lecture des metriques Actuator (profil loadtest) en fin de test.
//
// Aucun mot de passe, jeton ou secret n'est ecrit dans ces fichiers.

import http from 'k6/http';
import { check, fail } from 'k6';

// ---------------------------------------------------------------------------
// Cible et liste blanche
// ---------------------------------------------------------------------------

const HOTES_AUTORISES = ['localhost', '127.0.0.1', 'host.docker.internal'].concat(
  (__ENV.ALLOWED_HOSTS || '')
    .split(',')
    .map((h) => h.trim().toLowerCase())
    .filter(Boolean),
);

function hoteDe(url) {
  const m = /^https?:\/\/(\[[^\]]+\]|[^/:?#]+)(?::\d+)?(?:[/?#].*)?$/i.exec(url || '');
  if (!m) fail(`URL invalide : "${url}"`);
  return m[1].toLowerCase();
}

function cibleAutorisee(url, variable) {
  const hote = hoteDe(url);
  if (!HOTES_AUTORISES.includes(hote)) {
    // Arret immediat, avant toute requete (code de VU initial).
    throw new Error(
      `${variable} vise "${hote}", qui n'est pas dans la liste blanche ` +
        `(${HOTES_AUTORISES.join(', ')}). Ajoutez-le a ALLOWED_HOSTS ` +
        `UNIQUEMENT s'il s'agit d'un environnement de test, jamais de la production.`,
    );
  }
  return url.replace(/\/+$/, '');
}

// URL de l'API telle que la voit un client : nginx du front + /api/v1.
export const BASE_URL = cibleAutorisee(__ENV.BASE_URL || 'http://localhost:8380/api/v1', 'BASE_URL');

// Metriques Prometheus du backend (profil loadtest), facultatif.
export const METRIQUES_URL = __ENV.METRIQUES_URL
  ? cibleAutorisee(__ENV.METRIQUES_URL, 'METRIQUES_URL')
  : '';

/** A appeler dans setup() : affiche une seule fois l'hote vise. */
export function annoncerCible(nomScenario) {
  console.log(`[${nomScenario}] cible : ${hoteDe(BASE_URL)} (${BASE_URL})`);
  if (METRIQUES_URL) console.log(`[${nomScenario}] metriques : ${METRIQUES_URL}`);
}

// ---------------------------------------------------------------------------
// Seuils communs
// ---------------------------------------------------------------------------

export const SEUILS_COMMUNS = {
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
};

export const SEUIL_MENU = { 'http_req_duration{groupe:menu}': ['p(95)<300'] };
export const SEUIL_COMMANDE = { 'http_req_duration{name:POST /commandes}': ['p(95)<800'] };

// Duree d'un palier : PALIER=30s pour un essai rapide, 1m/2m pour une vraie mesure.
export const PALIER = __ENV.PALIER || '1m';

// ---------------------------------------------------------------------------
// Requetes
// ---------------------------------------------------------------------------

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function entetes(jeton, autres) {
  const h = Object.assign({}, JSON_HEADERS, autres || {});
  if (jeton) h.Authorization = `Bearer ${jeton}`;
  return h;
}

/** Lecture JSON tolerante (null si le corps n'est pas du JSON). */
export function corps(reponse) {
  try {
    return reponse.json();
  } catch (e) {
    return null;
  }
}

export function codeErreur(reponse) {
  const c = corps(reponse);
  return c && c.code ? c.code : '';
}

// ---------------------------------------------------------------------------
// Connexion du staff
// ---------------------------------------------------------------------------

const COMPTES = {
  CUISINE: 'cuisine',
  GERANT: 'gerant',
  MANAGER: 'manager',
  LIVREUR: 'livreur',
  VENDEUR: 'vendeur',
};

/**
 * Connexion d'un compte staff cree au demarrage du backend (profil loadtest).
 * Mot de passe lu dans K6_MDP_<ROLE> ; identifiant surchargeable par K6_ID_<ROLE>.
 */
export function connexion(role) {
  const motDePasse = __ENV[`K6_MDP_${role}`];
  if (!motDePasse) fail(`Variable K6_MDP_${role} absente : mot de passe du compte ${role}.`);
  const identifiant = __ENV[`K6_ID_${role}`] || COMPTES[role];
  const r = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ username: identifiant, password: motDePasse }),
    { headers: JSON_HEADERS, tags: { name: 'POST /auth/login', groupe: 'connexion' } },
  );
  const c = corps(r);
  if (r.status !== 200 || !c || !c.token) {
    fail(`Connexion ${role} refusee (HTTP ${r.status}). Verifiez K6_MDP_${role}.`);
  }
  return c.token;
}

export function connexions(roles) {
  const jetons = {};
  roles.forEach((role) => {
    jetons[role] = connexion(role);
  });
  return jetons;
}

// ---------------------------------------------------------------------------
// Catalogue
// ---------------------------------------------------------------------------

export const ROUTES_MENU = [
  '/categories',
  '/produits',
  '/plats-du-jour',
  '/accompagnements-condiments',
  '/annonces',
  '/regions',
];

/** Une lecture complete du menu, comme l'ouverture de l'App Client. */
export function lireMenu() {
  const reponses = http.batch(
    ROUTES_MENU.map((route) => [
      'GET',
      `${BASE_URL}${route}?statut=ACTIF`,
      null,
      { tags: { name: `GET ${route}`, groupe: 'menu' } },
    ]),
  );
  reponses.forEach((r, i) => {
    check(r, { [`menu ${ROUTES_MENU[i]} : 200`]: (x) => x.status === 200 });
  });
  return reponses;
}

/** Catalogue commandable : produits ACTIF en stock + secteurs de Dakar. */
export function lireCatalogue() {
  const [rp, rs] = http.batch([
    ['GET', `${BASE_URL}/produits?statut=ACTIF`, null, { tags: { name: 'GET /produits', groupe: 'menu' } }],
    [
      'GET',
      `${BASE_URL}/regions/DAKAR/secteurs`,
      null,
      { tags: { name: 'GET /regions/{code}/secteurs', groupe: 'menu' } },
    ],
  ]);
  check(rp, { 'catalogue produits : 200': (x) => x.status === 200 });
  check(rs, { 'catalogue secteurs : 200': (x) => x.status === 200 });
  const produits = (corps(rp) || []).filter((p) => p.statut === 'ACTIF' && p.disponibilite !== 'RUPTURE');
  const secteurs = (corps(rs) || []).filter((s) => !s.estMasque);
  return { produits, secteurs };
}

// ---------------------------------------------------------------------------
// Donnees clients
// ---------------------------------------------------------------------------

const PRENOMS = ['Awa', 'Moussa', 'Fatou', 'Ibrahima', 'Aminata', 'Cheikh', 'Khady', 'Ousmane', 'Mariama', 'Abdou'];
const OPERATEURS = ['70', '75', '76', '77', '78'];

export function hasard(n) {
  return Math.floor(Math.random() * n);
}

export function auHasard(liste) {
  return liste[hasard(liste.length)];
}

/** Numero senegalais valide et varie : +221 7X XXX XX XX. */
export function telephone() {
  let fin = '';
  for (let i = 0; i < 7; i += 1) fin += hasard(10);
  return `+221${auHasard(OPERATEURS)}${fin}`;
}

/** Cle Idempotency-Key unique (8 a 100 caracteres, [A-Za-z0-9-_:.]). */
export function cleIdempotence() {
  const iter = typeof __ITER === 'undefined' ? 's' : __ITER;
  return `k6-${__VU}-${iter}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Commande APP realiste : 1 a 3 produits distincts, quantites 1 a 3,
 * LIVRAISON (secteur de Dakar) ou RETRAIT selon `mode`.
 * Renvoie { corps, attendu: { sousTotal, fraisLivraison, total } }.
 */
export function nouvelleCommande(catalogue, mode, produitsImposes) {
  const choisis = produitsImposes || [];
  if (!produitsImposes) {
    const nb = 1 + hasard(3);
    const melange = catalogue.produits.slice().sort(() => Math.random() - 0.5);
    for (let i = 0; i < nb && i < melange.length; i += 1) choisis.push(melange[i]);
  }
  const lignes = choisis.map((p) => ({ produitId: p.id, quantite: 1 + hasard(3) }));
  const commande = {
    canal: 'APP',
    mode,
    moyenPaiement: auHasard(['wave', 'orange-money', 'mixx-yas', 'carte']),
    clientPrenom: auHasard(PRENOMS),
    clientTelephone: telephone(),
    lignes,
  };
  let fraisLivraison = 0;
  if (mode === 'LIVRAISON') {
    const secteur = auHasard(catalogue.secteurs);
    commande.secteurId = secteur.id;
    commande.adresseLivraison = secteur.nom;
    fraisLivraison = secteur.prixLivraison;
  }
  const attendu = calculerTotal(choisis, lignes, fraisLivraison);
  return { corps: commande, attendu };
}

/** Total attendu, calcule depuis le catalogue LU (le serveur doit trouver le meme). */
export function calculerTotal(produits, lignes, fraisLivraison) {
  const prix = {};
  produits.forEach((p) => {
    prix[p.id] = p.prix;
  });
  const sousTotal = lignes.reduce((s, l) => s + prix[l.produitId] * l.quantite, 0);
  return { sousTotal, fraisLivraison, total: sousTotal + fraisLivraison };
}

/** POST /commandes ; `attendus` = statuts HTTP normaux pour ce scenario. */
export function envoyerCommande(commande, cle, attendus) {
  return http.post(`${BASE_URL}/commandes`, JSON.stringify(commande), {
    headers: entetes(null, { 'Idempotency-Key': cle || cleIdempotence() }),
    tags: { name: 'POST /commandes', groupe: 'commande' },
    responseCallback: http.expectedStatuses(...(attendus || [201])),
  });
}

/** PATCH /commandes/{id}/statut par un compte staff. */
export function transition(jeton, id, statut, attendus, nom) {
  return http.patch(`${BASE_URL}/commandes/${id}/statut`, JSON.stringify({ statut }), {
    headers: entetes(jeton),
    tags: { name: nom || 'PATCH /commandes/{id}/statut', groupe: 'transition' },
    responseCallback: http.expectedStatuses(...(attendus || [200])),
  });
}

/** File de travail d'un compte staff (GET /commandes?statut=...). */
export function fileDeTravail(jeton, statut, nom, groupe) {
  const r = http.get(`${BASE_URL}/commandes?statut=${statut}`, {
    headers: entetes(jeton),
    tags: { name: nom || 'GET /commandes (file staff)', groupe: groupe || 'file' },
  });
  check(r, { 'file de travail : 200': (x) => x.status === 200 });
  return corps(r) || [];
}

// ---------------------------------------------------------------------------
// Metriques Actuator (profil loadtest)
// ---------------------------------------------------------------------------

/** Somme des series Prometheus `nom{...}` dont les etiquettes contiennent `filtre`. */
function somme(texte, nom, filtre) {
  let total = 0;
  texte.split('\n').forEach((ligne) => {
    if (ligne.startsWith(`${nom}{`) && (!filtre || ligne.includes(filtre))) {
      total += parseFloat(ligne.slice(ligne.lastIndexOf(' ') + 1)) || 0;
    }
  });
  return total;
}

/**
 * En fin de test (teardown) : efficacite du cache, pool Hikari, CPU.
 * Sans METRIQUES_URL, ne fait rien.
 */
export function resumerMetriques(nomScenario) {
  if (!METRIQUES_URL) return;
  const r = http.get(METRIQUES_URL, { tags: { name: 'metriques', groupe: 'metriques' } });
  if (r.status !== 200) {
    console.warn(`[${nomScenario}] metriques illisibles (HTTP ${r.status})`);
    return;
  }
  const t = r.body;
  const caches = {};
  t.split('\n').forEach((ligne) => {
    const m = /^cache_gets_total\{cache="([^"]+)",result="(hit|miss)"\} (\S+)/.exec(ligne);
    if (m) {
      caches[m[1]] = caches[m[1]] || { hit: 0, miss: 0 };
      caches[m[1]][m[2]] += parseFloat(m[3]);
    }
  });
  Object.keys(caches).forEach((nom) => {
    const { hit, miss } = caches[nom];
    const taux = hit + miss > 0 ? ((100 * hit) / (hit + miss)).toFixed(1) : '-';
    console.log(`[${nomScenario}] cache ${nom} : ${taux} % de succes (${hit} succes / ${miss} echecs)`);
  });
  console.log(
    `[${nomScenario}] Hikari : actives=${somme(t, 'hikaricp_connections_active')} ` +
      `en attente=${somme(t, 'hikaricp_connections_pending')} ` +
      `max=${somme(t, 'hikaricp_connections_max')} ` +
      `delais d'attente depasses=${somme(t, 'hikaricp_connections_timeout_total')}`,
  );
  console.log(
    `[${nomScenario}] CPU processus=${(100 * somme(t, 'process_cpu_usage')).toFixed(0)} % ` +
      `requetes SQL=${somme(t, 'hibernate_statements_total', 'status="success"') || somme(t, 'hibernate_statements_total')}`,
  );
}
