// La liste des arrondissements officiels de l'outil reste celle de la
// migration V6 (sinon : node outil-client/reference/generer.mjs).
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { it } from 'node:test';
import { FICHIER, lireV6 } from '../reference/generer.mjs';
import { idSecteur } from '../lib/validation.mjs';

it('reference/secteurs-officiels.json est aligne sur V6__zones_officielles.sql', () => {
  const v6 = lireV6();
  assert.deepEqual(JSON.parse(readFileSync(FICHIER, 'utf8')), v6);
  assert.equal(v6.regions.length, 14);
  assert.equal(v6.secteurs.length, 125);
});

it('identifiant de zone identique a Secteur.identifiant() du backend', () => {
  assert.equal(idSecteur('DAKAR', 'Plateau/Gorée'), 'DAKAR-PLATEAU-GOREE');
  assert.equal(idSecteur('THIES', 'Cité Malick Sy'), 'THIES-CITE-MALICK-SY');
  for (const s of lireV6().secteurs) assert.equal(idSecteur(s.region, s.nom), s.id, s.nom);
});
