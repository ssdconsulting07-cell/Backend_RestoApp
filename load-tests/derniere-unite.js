// Derniere unite : rafale de commandes sur un produit pendant que le GERANT
// le passe en RUPTURE.
//  - Une commande commencee APRES le passage en rupture (le client a deja vu
//    le produit en RUPTURE) est toujours refusee : 400 PRODUIT_INDISPONIBLE.
//  - Une commande acceptee juste avant reste legitime (PAYEE).
//  - Deux transitions simultanees sur la meme commande : un seul succes, l'autre
//    409 STATUT_DEJA_APPLIQUE.
// Codes attendus ici : 400 (PRODUIT_INDISPONIBLE) et 409.
import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';
import {
  BASE_URL, SEUILS_COMMUNS, SEUIL_COMMANDE, annoncerCible, connexions, lireCatalogue,
  nouvelleCommande, envoyerCommande, transition, entetes, corps, codeErreur, resumerMetriques,
} from './lib/commun.js';

const NB_DOUBLES = 20;
const violations = new Counter('violations_rupture');
const doublesAppliquees = new Counter('transitions_doubles_appliquees');
const accepteesAvant = new Counter('commandes_acceptees_avant_rupture');
const refuseesApres = new Counter('commandes_refusees_rupture');

export const options = {
  scenarios: {
    rafale: {
      executor: 'constant-arrival-rate',
      exec: 'rafale',
      rate: Number(__ENV.DEBIT || 20),
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 40,
      maxVUs: 100,
    },
    gerant: { executor: 'per-vu-iterations', exec: 'gerant', vus: 1, iterations: 1, startTime: '10s' },
    doubles: {
      executor: 'shared-iterations',
      exec: 'doubles',
      vus: 5,
      iterations: NB_DOUBLES,
      startTime: '35s',
    },
  },
  thresholds: Object.assign({}, SEUILS_COMMUNS, SEUIL_COMMANDE, {
    violations_rupture: ['count==0'],
    transitions_doubles_appliquees: ['count==0'],
    commandes_refusees_rupture: ['count>0'],
  }),
};

function lireProduit(id) {
  const r = http.get(`${BASE_URL}/produits/${id}`, { tags: { name: 'GET /produits/{id}', groupe: 'produit' } });
  return corps(r);
}

export function setup() {
  annoncerCible('derniere-unite');
  const jetons = connexions(['GERANT', 'CUISINE']);
  const catalogue = lireCatalogue();
  const produit = __ENV.PRODUIT_ID
    ? catalogue.produits.find((p) => p.id === __ENV.PRODUIT_ID)
    : catalogue.produits[0];
  if (!produit) fail('Aucun produit en stock pour le test (PRODUIT_ID ?).');
  // Commandes PAYEE pour les transitions simultanees.
  const payees = [];
  for (let i = 0; i < NB_DOUBLES; i += 1) {
    const autre = catalogue.produits.find((p) => p.id !== produit.id);
    const c = corps(envoyerCommande(nouvelleCommande(catalogue, 'RETRAIT', [autre]).corps));
    if (!c || !c.id) fail('Creation des commandes de preparation impossible.');
    payees.push(c.id);
  }
  console.log(`[derniere-unite] produit vise : ${produit.id} (${produit.nom})`);
  return { jetons, catalogue, produit, payees };
}

// Le client relit le produit (comme l'App Client) puis commande.
export function rafale(d) {
  const vu = lireProduit(d.produit.id);
  const vuEnRupture = vu && vu.disponibilite === 'RUPTURE';
  const { corps: commande } = nouvelleCommande(d.catalogue, 'RETRAIT', [d.produit]);
  const r = envoyerCommande(commande, null, [201, 400]);
  const code = codeErreur(r);
  check(r, { 'rafale : 201 ou 400 PRODUIT_INDISPONIBLE': (x) => x.status === 201 || code === 'PRODUIT_INDISPONIBLE' });
  if (r.status === 201) {
    if (vuEnRupture) violations.add(1); // commencee apres la rupture et pourtant acceptee
    else accepteesAvant.add(1);
    check(r, { 'acceptee avant la rupture : PAYEE': (x) => (corps(x) || {}).statut === 'PAYEE' });
  } else if (code === 'PRODUIT_INDISPONIBLE') {
    refuseesApres.add(1);
  }
}

function modifierDisponibilite(jeton, produitId, disponibilite) {
  const p = lireProduit(produitId);
  const saisie = {
    nom: p.nom,
    description: p.description,
    prix: p.prix,
    categorieId: p.categorieId,
    photoUrl: p.photoUrl,
    disponibilite,
    tendance: p.tendance,
    version: p.version,
  };
  return http.put(`${BASE_URL}/produits/${produitId}`, JSON.stringify(saisie), {
    headers: entetes(jeton),
    tags: { name: 'PUT /produits/{id}', groupe: 'gestion' },
  });
}

export function gerant(d) {
  const r = modifierDisponibilite(d.jetons.GERANT, d.produit.id, 'RUPTURE');
  check(r, { 'GERANT : passage en RUPTURE 200': (x) => x.status === 200 });
}

export function doubles(d) {
  const id = d.payees[exec.scenario.iterationInTest];
  const params = {
    headers: entetes(d.jetons.CUISINE),
    tags: { name: 'PATCH statut (simultane)', groupe: 'transition' },
    responseCallback: http.expectedStatuses(200, 409),
  };
  const corpsJson = JSON.stringify({ statut: 'EN_PREPARATION' });
  const [a, b] = http.batch([
    ['PATCH', `${BASE_URL}/commandes/${id}/statut`, corpsJson, params],
    ['PATCH', `${BASE_URL}/commandes/${id}/statut`, corpsJson, params],
  ]);
  const succes = [a, b].filter((x) => x.status === 200).length;
  const conflits = [a, b].filter((x) => x.status === 409 && codeErreur(x) === 'STATUT_DEJA_APPLIQUE').length;
  if (succes !== 1) doublesAppliquees.add(1);
  check(null, { 'transitions simultanees : 1 succes + 1 x 409': () => succes === 1 && conflits === 1 });
}

export function teardown(d) {
  // Remise en stock : l'environnement reste utilisable (il est de toute facon
  // repris de zero par "docker compose down -v" entre deux passages).
  modifierDisponibilite(d.jetons.GERANT, d.produit.id, 'EN_STOCK');
  resumerMetriques('derniere-unite');
}
