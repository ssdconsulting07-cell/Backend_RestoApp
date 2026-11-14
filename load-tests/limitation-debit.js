// Limitation de debit : A LANCER A PART, sur un backend en profil dev NORMAL
// (pas loadtest : ce profil leve les limites). Au-dela de 10 commandes et de
// 10 connexions par minute depuis une meme IP : 429 TROP_DE_REQUETES +
// Retry-After. Attendre une minute entre deux lancements.
//
// Aucune commande reelle n'est creee (corps vide -> 400) et aucune connexion
// n'aboutit (identifiants volontairement faux -> 401) : seuls les compteurs
// de la limitation sont sollicites.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, SEUILS_COMMUNS, annoncerCible, entetes, codeErreur } from './lib/commun.js';

const LIMITE = Number(__ENV.LIMITE || 10);
const ENVOIS = LIMITE + 3;

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: SEUILS_COMMUNS,
};

export function setup() {
  annoncerCible('limitation-debit');
}

function rafale(route, corpsJson, nom, codeNormal) {
  const statuts = [];
  let retryAfter = null;
  for (let i = 0; i < ENVOIS; i += 1) {
    const r = http.post(`${BASE_URL}${route}`, corpsJson, {
      headers: entetes(),
      tags: { name: nom, groupe: 'limitation' },
      responseCallback: http.expectedStatuses(codeNormal, 429),
    });
    statuts.push(r.status);
    if (r.status === 429) {
      retryAfter = r.headers['Retry-After'];
      check(r, { [`${nom} : code TROP_DE_REQUETES`]: (x) => codeErreur(x) === 'TROP_DE_REQUETES' });
    }
  }
  const passees = statuts.filter((s) => s !== 429).length;
  console.log(`[limitation-debit] ${nom} : ${statuts.join(' ')}`);
  check(null, {
    [`${nom} : au plus ${LIMITE} requetes passent`]: () => passees <= LIMITE,
    [`${nom} : 429 au-dela de ${LIMITE}`]: () => statuts[statuts.length - 1] === 429,
    [`${nom} : en-tete Retry-After`]: () => !!retryAfter,
  });
}

export default function () {
  rafale('/commandes', '{}', 'POST /commandes', 400);
  const faux = JSON.stringify({ username: 'k6-inconnu', password: 'k6-faux' });
  rafale('/auth/login', faux, 'POST /auth/login', 401);
}
