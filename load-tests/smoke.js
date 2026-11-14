// Fumee : 2 utilisateurs, 30 s. Menu + 1 commande + 1 transition (Cuisine).
// Lance en CI a chaque PR (job "smoke" de backend-ci.yml).
import { check, sleep } from 'k6';
import {
  SEUILS_COMMUNS, SEUIL_MENU, SEUIL_COMMANDE, annoncerCible, connexion, lireMenu,
  lireCatalogue, nouvelleCommande, envoyerCommande, transition, corps, auHasard,
} from './lib/commun.js';

export const options = {
  vus: 2,
  duration: __ENV.DUREE || '30s',
  thresholds: Object.assign({}, SEUILS_COMMUNS, SEUIL_MENU, SEUIL_COMMANDE),
};

export function setup() {
  annoncerCible('smoke');
  return { jetonCuisine: connexion('CUISINE') };
}

export default function (donnees) {
  lireMenu();
  const catalogue = lireCatalogue();
  const { corps: commande, attendu } = nouvelleCommande(catalogue, auHasard(['LIVRAISON', 'RETRAIT']));
  const r = envoyerCommande(commande);
  const creee = corps(r) || {};
  check(r, {
    'commande : 201': (x) => x.status === 201,
    'commande : PAYEE (paiement simule)': () => creee.statut === 'PAYEE',
    'commande : total = catalogue': () => creee.total === attendu.total,
  });
  if (creee.id) {
    const t = transition(donnees.jetonCuisine, creee.id, 'EN_PREPARATION');
    check(t, { 'transition PAYEE -> EN_PREPARATION : 200': (x) => x.status === 200 });
  }
  sleep(1);
}
