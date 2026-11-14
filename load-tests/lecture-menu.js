// Lecture du menu (ouverture de l'App Client) en paliers : 10 -> 50 -> 200 -> 500
// utilisateurs. PALIER=1m (defaut) ou 2m par palier. Efficacite du cache lue
// dans les metriques Actuator en fin de test (METRIQUES_URL).
import { sleep } from 'k6';
import { SEUILS_COMMUNS, SEUIL_MENU, PALIER, annoncerCible, lireMenu, resumerMetriques } from './lib/commun.js';

const PALIERS = (__ENV.PALIERS || '10,50,200,500').split(',').map(Number);

export const options = {
  scenarios: {
    menu: {
      executor: 'ramping-vus',
      startVUs: 0,
      // Montee rapide (10 s) puis maintien de chaque palier.
      stages: PALIERS.flatMap((n) => [{ duration: '10s', target: n }, { duration: PALIER, target: n }]),
      gracefulRampDown: '10s',
    },
  },
  thresholds: Object.assign({}, SEUILS_COMMUNS, SEUIL_MENU),
};

export function setup() {
  annoncerCible('lecture-menu');
  resumerMetriques('lecture-menu (avant)');
}

export default function () {
  lireMenu();
  // Un humain lit le menu avant de rouvrir l'app : 1 a 3 s.
  sleep(1 + Math.random() * 2);
}

export function teardown() {
  resumerMetriques('lecture-menu (apres)');
}
