// Parcours client realiste : lecture du catalogue, commande (50 % livraison,
// 50 % retrait), telephones varies, Idempotency-Key unique, total verifie
// contre le catalogue lu, puis suivi de la commande avec son jeton.
import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  BASE_URL, SEUILS_COMMUNS, SEUIL_MENU, SEUIL_COMMANDE, PALIER, annoncerCible, lireCatalogue,
  nouvelleCommande, envoyerCommande, corps, resumerMetriques,
} from './lib/commun.js';

const PALIERS = (__ENV.PALIERS || '10,25,50').split(',').map(Number);

export const options = {
  scenarios: {
    clients: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: PALIERS.flatMap((n) => [{ duration: '10s', target: n }, { duration: PALIER, target: n }]),
      gracefulRampDown: '10s',
    },
  },
  thresholds: Object.assign({}, SEUILS_COMMUNS, SEUIL_MENU, SEUIL_COMMANDE),
};

export function setup() {
  annoncerCible('passage-commande');
}

export default function () {
  const catalogue = lireCatalogue();
  sleep(2 + Math.random() * 3); // choix des plats
  const mode = Math.random() < 0.5 ? 'LIVRAISON' : 'RETRAIT';
  const { corps: commande, attendu } = nouvelleCommande(catalogue, mode);
  const r = envoyerCommande(commande);
  const c = corps(r) || {};
  check(r, {
    'commande : 201': (x) => x.status === 201,
    'commande : PAYEE': () => c.statut === 'PAYEE',
    'commande : sous-total = catalogue': () => c.sousTotal === attendu.sousTotal,
    'commande : frais de livraison = secteur': () => c.fraisLivraison === attendu.fraisLivraison,
    'commande : total = catalogue': () => c.total === attendu.total,
    'commande : jeton de suivi': () => typeof c.jetonSuivi === 'string' && c.jetonSuivi.length > 20,
  });
  if (c.id && c.jetonSuivi) {
    sleep(1);
    const s = http.get(`${BASE_URL}/commandes/${c.id}`, {
      headers: { 'X-Jeton-Suivi': c.jetonSuivi },
      tags: { name: 'GET /commandes/{id} (suivi)', groupe: 'suivi' },
    });
    check(s, { 'suivi : 200': (x) => x.status === 200 });
  }
  sleep(1 + Math.random() * 2);
}

export function teardown() {
  resumerMetriques('passage-commande');
}
