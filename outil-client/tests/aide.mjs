// Outils des tests : une copie jetable de clients/exemple a modifier.
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const RACINE = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
export const EXEMPLE = join(RACINE, 'clients', 'exemple');

const temporaires = [];

/** Copie de l'exemple ; `modifier(json, fichier)` change un JSON (restaurant.json ou donnees/...). */
export function copieExemple() {
  const base = mkdtempSync(join(tmpdir(), 'outil-client-'));
  temporaires.push(base);
  const dossier = join(base, 'exemple');
  cpSync(EXEMPLE, dossier, { recursive: true });
  return {
    dossier,
    base,
    modifier(fichier, fn) {
      const chemin = join(dossier, fichier);
      const json = JSON.parse(readFileSync(chemin, 'utf8'));
      const retour = fn(json);
      writeFileSync(chemin, JSON.stringify(retour === undefined ? json : retour, null, 2));
    },
    ecrire(fichier, contenu) {
      writeFileSync(join(dossier, fichier), contenu);
    },
    supprimer(fichier) {
      rmSync(join(dossier, fichier), { force: true });
    },
  };
}

export function nettoyer() {
  temporaires.splice(0).forEach((d) => rmSync(d, { recursive: true, force: true }));
}
