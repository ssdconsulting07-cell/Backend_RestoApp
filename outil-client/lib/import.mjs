// Dossier du restaurant -> donnees/import.json (+ photos/) lu par le backend
// au demarrage (restaurant/ImportRestaurant.java, format 1). Chaque fiche
// garde son id du dossier comme cle stable : l'import est rejouable sans
// doublon et n'ecrase jamais une fiche modifiee par le Gerant (sauf --forcer).
import { randomBytes } from 'node:crypto';
import { copyFileSync, mkdirSync } from 'node:fs';
import { basename, extname, join } from 'node:path';
import { secteurOfficiel } from './validation.mjs';

export const FORMAT_IMPORT = 1;

const statut = (s) => s || 'ACTIF';
const jours = (liste) => (liste || []).map((j) => j.toUpperCase());

/** Jeton unique d'un passage : le backend n'applique chaque passage qu'une fois. */
export const nouveauJeton = (maintenant = new Date()) => `${maintenant.toISOString()}-${randomBytes(4).toString('hex')}`;

/**
 * Construit l'objet import.json et copie les photos dans `sortieDonnees/photos`.
 * `membres` : comptes deja prepares (empreintes BCrypt, voir comptes.mjs).
 */
export function construireImport({ restaurant, donnees, membres, dossier, sortieDonnees, forcer, jeton }) {
  const photos = new Map();
  const photo = (source) => {
    if (!source || source.startsWith('https://')) return source || null;
    if (!photos.has(source)) {
      // Nom plat et unique (deux photos "plat.jpg" de dossiers differents ne s'ecrasent pas).
      const nom = `${String(photos.size + 1).padStart(3, '0')}-${basename(source, extname(source))
        .toLowerCase().replace(/[^a-z0-9-]+/g, '-')}${extname(source).toLowerCase()}`;
      mkdirSync(join(sortieDonnees, 'photos'), { recursive: true });
      copyFileSync(join(dossier, source), join(sortieDonnees, 'photos', nom));
      photos.set(source, `photos/${nom}`);
    }
    return photos.get(source);
  };

  const services = restaurant.horaires.services;
  const parRegion = new Map();
  for (const z of restaurant.modes.livraison ? restaurant.livraison?.zones || [] : []) {
    if (!parRegion.has(z.region)) parRegion.set(z.region, []);
    const officiel = secteurOfficiel(z.region, z.secteur);
    parRegion.get(z.region).push(officiel
      ? { officiel: officiel.id, departement: null, nom: null, frais: z.frais }
      : { officiel: null, departement: z.departement, nom: z.secteur, frais: z.frais });
  }

  return {
    format: FORMAT_IMPORT,
    restaurant: restaurant.identifiant,
    jeton,
    forcer: Boolean(forcer),
    horaires: [
      { service: 'DEJEUNER', debut: services.dejeuner.debut, fin: services.dejeuner.fin },
      { service: 'DINER', debut: services.diner.debut, fin: services.diner.fin },
    ],
    categories: donnees.categories.map((c) => ({
      cle: c.id, nom: c.nom, accompagnementPropose: Boolean(c.accompagnements), statut: statut(c.statut),
    })),
    produits: donnees.produits.map((p) => ({
      cle: p.id,
      nom: p.nom,
      description: p.description || null,
      prix: p.prix,
      categorie: p.categorie,
      photo: photo(p.photo),
      tendance: p.tendance === true ? 'OUI' : p.tendance === false ? 'NON' : 'AUTO',
      disponibilite: p.disponible === false ? 'RUPTURE' : 'EN_STOCK',
      statut: statut(p.statut),
    })),
    accompagnements: donnees.accompagnements.map((a) => ({
      cle: a.id, nom: a.nom, prix: a.prix, type: a.type.toUpperCase(), statut: statut(a.statut),
    })),
    platsDuJour: donnees.platsDuJour.map((p) => ({
      cle: p.id,
      nom: p.nom,
      description: p.description || null,
      prix: p.prix,
      photo: photo(p.photo),
      jours: jours(p.jours),
      dejeuner: !p.services || p.services.includes('dejeuner'),
      diner: !p.services || p.services.includes('diner'),
      statut: statut(p.statut),
    })),
    annonces: donnees.annonces.map((a) => ({
      cle: a.id,
      titre: a.titre,
      statut: statut(a.statut),
      slides: a.slides.map((s) => ({ titre: s.titre, message: s.message || null, image: photo(s.image) })),
    })),
    zones: [...parRegion.entries()].map(([code, secteurs]) => ({
      code,
      masquerAutres: restaurant.livraison?.masquerAutresZones !== false,
      secteurs,
    })),
    personnel: membres,
  };
}
