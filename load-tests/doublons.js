// Doublons : la meme commande envoyee 5 fois en parallele avec la meme
// Idempotency-Key ne cree qu'UNE commande (meme id partout) ; la meme cle
// avec un autre contenu est refusee (409 IDEMPOTENCE_CONFLIT).
// Pendant le traitement du premier envoi, les autres peuvent recevoir
// 409 REQUETE_EN_COURS : ils reessaient, comme l'App Client.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import {
  BASE_URL, SEUILS_COMMUNS, annoncerCible, lireCatalogue, nouvelleCommande, cleIdempotence,
  entetes, corps, codeErreur, resumerMetriques,
} from './lib/commun.js';

const doublonsCrees = new Counter('doublons_crees');

export const options = {
  scenarios: {
    doublons: { executor: 'shared-iterations', vus: 5, iterations: Number(__ENV.ITERATIONS || 30) },
  },
  thresholds: Object.assign({}, SEUILS_COMMUNS, { doublons_crees: ['count==0'] }),
};

export function setup() {
  annoncerCible('doublons');
  return { catalogue: lireCatalogue() };
}

function envoi(commande, cle) {
  return [
    'POST',
    `${BASE_URL}/commandes`,
    JSON.stringify(commande),
    {
      headers: entetes(null, { 'Idempotency-Key': cle }),
      tags: { name: 'POST /commandes (meme cle)', groupe: 'commande' },
      responseCallback: http.expectedStatuses(201, 409),
    },
  ];
}

export default function (d) {
  const cle = cleIdempotence();
  const mode = Math.random() < 0.5 ? 'LIVRAISON' : 'RETRAIT';
  const commande = nouvelleCommande(d.catalogue, mode).corps;

  let reponses = http.batch([1, 2, 3, 4, 5].map(() => envoi(commande, cle)));
  // Reessai des envois tombes pendant le traitement du premier.
  for (let essai = 0; essai < 10; essai += 1) {
    const enCours = reponses.filter((r) => r.status === 409 && codeErreur(r) === 'REQUETE_EN_COURS');
    if (enCours.length === 0) break;
    sleep(0.2);
    const relances = http.batch(enCours.map(() => envoi(commande, cle)));
    reponses = reponses.filter((r) => !enCours.includes(r)).concat(relances);
  }
  const ids = reponses.map((r) => (corps(r) || {}).id);
  check(null, {
    'doublons : 5 reponses 201': () => reponses.every((r) => r.status === 201),
    'doublons : meme id partout': () => ids.every((id) => id && id === ids[0]),
  });

  // Une seule commande pour ce numero (historique client sans compte).
  const tel = encodeURIComponent(commande.clientTelephone);
  const h = http.get(`${BASE_URL}/commandes?clientTelephone=${tel}`, {
    tags: { name: 'GET /commandes (historique client)', groupe: 'suivi' },
  });
  const nb = (corps(h) || []).length;
  if (nb !== 1) doublonsCrees.add(Math.max(0, nb - 1));
  check(h, { 'doublons : une seule commande en base': () => nb === 1 });

  // Meme cle, autre contenu : 409.
  const autre = Object.assign({}, commande, { clientPrenom: `${commande.clientPrenom}x` });
  const c = http.post(`${BASE_URL}/commandes`, JSON.stringify(autre), {
    headers: entetes(null, { 'Idempotency-Key': cle }),
    tags: { name: 'POST /commandes (cle reutilisee)', groupe: 'commande' },
    responseCallback: http.expectedStatuses(409),
  });
  check(c, {
    'meme cle, autre contenu : 409 IDEMPOTENCE_CONFLIT': (x) => x.status === 409 && codeErreur(x) === 'IDEMPOTENCE_CONFLIT',
  });
}

export function teardown() {
  resumerMetriques('doublons');
}
