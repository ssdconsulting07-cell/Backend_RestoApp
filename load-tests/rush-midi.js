// Rush de midi : des clients commandent pendant que les comptes CUISINE,
// LIVREUR et VENDEUR font avancer les commandes :
//   PAYEE -> EN_PREPARATION -> PRETE -> EN_LIVRAISON -> LIVREE (livraison)
//                                    -> RETIREE                 (retrait)
// Un compte Cuisine "observateur" interroge GET /commandes toutes les 500 ms
// (requetes etiquetees a part : groupe=sondage) et mesure le delai
// d'apparition d'une commande en Cuisine. Un "intrus" tente des transitions
// hors table : 403 attendu.
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  SEUILS_COMMUNS, SEUIL_MENU, SEUIL_COMMANDE, PALIER, annoncerCible, connexions, lireCatalogue,
  nouvelleCommande, envoyerCommande, transition, fileDeTravail, corps, resumerMetriques,
} from './lib/commun.js';

const PALIERS = (__ENV.PALIERS || '10,30,60').split(',').map(Number);
const DUREE_TOTALE = `${PALIERS.length * (10 + parseInt(PALIER, 10) * (PALIER.endsWith('m') ? 60 : 1)) + 20}s`;

const delaiApparition = new Trend('delai_apparition_cuisine', true);
const commandesTerminees = new Counter('commandes_terminees');

export const options = {
  scenarios: {
    clients: {
      executor: 'ramping-vus',
      exec: 'client',
      startVUs: 0,
      stages: PALIERS.flatMap((n) => [{ duration: '10s', target: n }, { duration: PALIER, target: n }]),
      gracefulRampDown: '10s',
    },
    cuisine: { executor: 'constant-vus', exec: 'cuisine', vus: 1, duration: DUREE_TOTALE },
    livreur: { executor: 'constant-vus', exec: 'livreur', vus: 1, duration: DUREE_TOTALE },
    vendeur: { executor: 'constant-vus', exec: 'vendeur', vus: 1, duration: DUREE_TOTALE },
    observateur: { executor: 'constant-vus', exec: 'observateur', vus: 1, duration: DUREE_TOTALE },
    intrus: { executor: 'constant-vus', exec: 'intrus', vus: 1, duration: DUREE_TOTALE },
  },
  thresholds: Object.assign({}, SEUILS_COMMUNS, SEUIL_MENU, SEUIL_COMMANDE, {
    // Mesures sans seuil bloquant : lues dans le rapport.
    'http_req_duration{groupe:sondage}': ['p(95)>=0'],
    delai_apparition_cuisine: ['p(95)>=0'],
  }),
};

export function setup() {
  annoncerCible('rush-midi');
  // Seules les commandes creees pendant ce scenario sont mesurees (une base
  // non videe garde des commandes PAYEE d'un passage precedent).
  return { jetons: connexions(['CUISINE', 'LIVREUR', 'VENDEUR']), debut: Date.now() };
}

// --- Clients ----------------------------------------------------------------

export function client() {
  const catalogue = lireCatalogue();
  sleep(2 + Math.random() * 3);
  const mode = Math.random() < 0.5 ? 'LIVRAISON' : 'RETRAIT';
  const { corps: commande, attendu } = nouvelleCommande(catalogue, mode);
  const r = envoyerCommande(commande);
  const c = corps(r) || {};
  check(r, {
    'commande : 201': (x) => x.status === 201,
    'commande : total = catalogue': () => c.total === attendu.total,
  });
  sleep(3 + Math.random() * 4);
}

// --- Staff : une tablette par role, transitions envoyees en parallele -------

function avancerTout(jeton, commandes, statut) {
  const lot = commandes.slice(0, 30);
  lot.forEach((c) => {
    const r = transition(jeton, c.id, statut);
    check(r, { [`transition -> ${statut} : 200`]: (x) => x.status === 200 });
    if (r.status === 200 && (statut === 'LIVREE' || statut === 'RETIREE')) commandesTerminees.add(1);
  });
}

export function cuisine(d) {
  const j = d.jetons.CUISINE;
  avancerTout(j, fileDeTravail(j, 'EN_PREPARATION'), 'PRETE');
  avancerTout(j, fileDeTravail(j, 'PAYEE'), 'EN_PREPARATION');
  sleep(2);
}

export function livreur(d) {
  const j = d.jetons.LIVREUR;
  avancerTout(j, fileDeTravail(j, 'EN_LIVRAISON'), 'LIVREE');
  avancerTout(j, fileDeTravail(j, 'PRETE').filter((c) => c.mode === 'LIVRAISON'), 'EN_LIVRAISON');
  sleep(2);
}

export function vendeur(d) {
  const j = d.jetons.VENDEUR;
  avancerTout(j, fileDeTravail(j, 'PRETE').filter((c) => c.mode === 'RETRAIT'), 'RETIREE');
  sleep(2);
}

// --- Observateur : delai d'apparition en Cuisine ----------------------------

const dejaVues = {};
let premierPassage = true;

function instant(iso) {
  // creeLe a 9 decimales : on garde les millisecondes.
  return Date.parse(String(iso).replace(/(\.\d{3})\d+/, '$1'));
}

export function observateur(d) {
  const liste = fileDeTravail(d.jetons.CUISINE, 'PAYEE', 'GET /commandes (sondage cuisine)', 'sondage');
  const maintenant = Date.now();
  liste.forEach((c) => {
    if (dejaVues[c.id]) return;
    dejaVues[c.id] = true;
    const cree = instant(c.creeLe);
    if (!premierPassage && cree >= d.debut) delaiApparition.add(maintenant - cree);
  });
  premierPassage = false;
  sleep(0.5);
}

// --- Intrus : transitions hors table -> 403 ---------------------------------
// Cibles choisies pour ne jamais etre "deja appliquees" (sinon 409) : une
// commande RETRAIT ne passe jamais EN_LIVRAISON, et seul le LIVREUR remet une
// commande (LIVREE). Le LIVREUR ne voit pas les commandes PAYEE (404) : il ne
// sert pas d'intrus ici.

export function intrus(d) {
  const payees = fileDeTravail(d.jetons.CUISINE, 'PAYEE', 'GET /commandes (intrus)');
  const retrait = payees.find((c) => c.mode === 'RETRAIT');
  const livraison = payees.find((c) => c.mode === 'LIVRAISON');
  if (retrait) {
    const r = transition(d.jetons.VENDEUR, retrait.id, 'EN_LIVRAISON', [403], 'PATCH statut (interdit)');
    check(r, { 'VENDEUR -> EN_LIVRAISON (retrait) : 403': (x) => x.status === 403 });
  }
  if (livraison) {
    const r = transition(d.jetons.CUISINE, livraison.id, 'LIVREE', [403], 'PATCH statut (interdit)');
    check(r, { 'CUISINE PAYEE -> LIVREE : 403': (x) => x.status === 403 });
  }
  sleep(5);
}

export function teardown() {
  resumerMetriques('rush-midi');
}
