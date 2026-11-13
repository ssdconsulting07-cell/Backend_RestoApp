// Genere les donnees de demonstration du backend (profil dev uniquement)
// a partir des fichiers de demo ACTUELS du Manager, pour que les deux
// restent identiques (et coherents avec db.json de l'App Client, lui-meme
// genere depuis le Manager).
//
//   node scripts/demo/generer-donnees-demo.mjs [chemin/vers/Manager_RestoApp]
//
// Produit src/main/resources/db/demo/R__demo_*.sql (migrations Flyway
// repetables : rejouees quand leur contenu change, sans doublon).
// A relancer quand les donnees de demo du Manager evoluent.

import { writeFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ici = dirname(fileURLToPath(import.meta.url));
const racineBackend = resolve(ici, '../..');
const manager = resolve(process.argv[2] || resolve(racineBackend, '../Manager_RestoApp'));
const gerant = (f) => import(pathToFileURL(resolve(manager, 'src/features/gerant', f)).href);

const { CATEGORIES_INITIALES, PRODUITS_INITIAUX } = await gerant('menuData.js');
const { PLATS_DU_JOUR_INITIAUX } = await gerant('platsDuJourData.js');
const { ACCOMPAGNEMENTS_CONDIMENTS_INITIAUX } = await gerant('accompagnementsCondimentsData.js');
const { CHAINES_INITIALES } = await gerant('annonceData.js');
const { REGIONS, ARRONDISSEMENTS_PAR_REGION, CENTRES_ARRONDISSEMENTS } = await gerant('senegalZones.js');
const { zonesLivraison, PRIX_PAR_DEFAUT } = await gerant('zonesLivraisonData.js');

// --- Utilitaires SQL ---------------------------------------------------------

const texte = (v) => (v === null || v === undefined || v === '' ? 'NULL'
  : `'${String(v).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`);
const entier = (v) => {
  if (!Number.isInteger(v)) throw new Error(`Montant non entier : ${v}`);
  return String(v);
};
const bit = (v) => (v ? "b'1'" : "b'0'");
const date = (iso) => texte((iso || '2026-09-01T00:00:00Z').replace('T', ' ').replace('Z', '').slice(0, 19));
const nombre = (v) => (v === null || v === undefined ? 'NULL' : String(v));
/** "categorie-12" -> 12 */
const idNum = (id) => {
  const m = String(id).match(/(\d+)$/);
  if (!m) throw new Error(`Identifiant sans numero : ${id}`);
  return Number(m[1]);
};
/** Meme regle que Secteur.identifiant() cote Java. */
const idSecteur = (region, nom) => `${region}-${nom.normalize('NFD').replace(/\p{M}/gu, '')
  .toUpperCase().replace(/[^A-Z0-9]+/g, '-').replace(/(^-|-$)/g, '')}`;

const entete = (sujet) => `-- Donnees de demonstration : ${sujet}.
-- GENERE par scripts/demo/generer-donnees-demo.mjs depuis Manager_RestoApp :
-- ne pas modifier a la main, relancer le script.
-- Profil dev uniquement (emplacement db/demo). Rejouable sans doublon.
`;

function upsert(table, colonnes, lignes, cle = 'id') {
  if (lignes.length === 0) return '';
  // Alias de ligne (MySQL >= 8.0.19) : la fonction VALUES() est depreciee.
  const maj = colonnes.filter((c) => c !== cle && c !== 'cree_le')
    .map((c) => `\`${c}\` = nouveau.\`${c}\``);
  if (colonnes.includes('version')) {
    maj.splice(maj.findIndex((m) => m.startsWith('`version`')), 1, `\`version\` = \`${table}\`.\`version\` + 1`);
  }
  return `INSERT INTO \`${table}\` (${colonnes.map((c) => `\`${c}\``).join(', ')}) VALUES\n`
    + lignes.map((l) => `  (${l.join(', ')})`).join(',\n')
    + `\nAS nouveau\nON DUPLICATE KEY UPDATE ${maj.join(', ')};\n`;
}

// --- Catalogue -----------------------------------------------------------------

const categories = CATEGORIES_INITIALES.map((c) => [
  idNum(c.id), texte(c.nom), texte(c.statut),
  // Regle des fronts ("seuls les burgers proposent un accompagnement"),
  // desormais portee par la categorie.
  bit(c.nom === 'Burgers'), '0', date(c.updatedAt), date(c.updatedAt),
]);
const produits = PRODUITS_INITIAUX.map((p) => [
  idNum(p.id), texte(p.nom), texte(p.description), entier(p.prix), idNum(p.categorieId), texte(p.photoUrl),
  texte(p.statut), texte(p.disponibilite || 'EN_STOCK'), bit(p.tendance), '0', date(p.updatedAt), date(p.updatedAt),
]);
const plats = PLATS_DU_JOUR_INITIAUX.map((p) => [
  idNum(p.id), texte(p.nom), texte(p.description), entier(p.prix), texte(p.photoUrl), texte(p.jour),
  texte(p.statut), '0', date(p.updatedAt), date(p.updatedAt),
]);
const accompagnements = ACCOMPAGNEMENTS_CONDIMENTS_INITIAUX.map((a) => [
  idNum(a.id), texte(a.nom), entier(a.prix), texte(a.type), texte(a.statut), '0', date(a.updatedAt), date(a.updatedAt),
]);

writeFileSync(resolve(racineBackend, 'src/main/resources/db/demo/R__demo_catalogue.sql'),
  `${entete('categories, produits, plats du jour, accompagnements et condiments')}
${upsert('categories', ['id', 'nom', 'statut', 'accompagnement_propose', 'version', 'cree_le', 'modifie_le'], categories)}
${upsert('produits', ['id', 'nom', 'description', 'prix', 'categorie_id', 'photo_url', 'statut', 'disponibilite', 'tendance', 'version', 'cree_le', 'modifie_le'], produits)}
${upsert('plats_du_jour', ['id', 'nom', 'description', 'prix', 'photo_url', 'jour', 'statut', 'version', 'cree_le', 'modifie_le'], plats)}
${upsert('accompagnements_condiments', ['id', 'nom', 'prix', 'type', 'statut', 'version', 'cree_le', 'modifie_le'], accompagnements)}`);

// --- Annonces -------------------------------------------------------------------

const annonces = CHAINES_INITIALES.map((c) => [
  idNum(c.id), texte(c.titre), texte(c.statut), c.publishedAt ? date(c.publishedAt) : 'NULL', '0',
  date(c.updatedAt), date(c.updatedAt),
]);
let slideId = 0;
const slides = CHAINES_INITIALES.flatMap((c) => c.slides.map((s, i) => [
  ++slideId, idNum(c.id), i, texte(s.titre), texte(s.message), texte(s.imageUrl),
]));
const idsAnnonces = CHAINES_INITIALES.map((c) => idNum(c.id)).join(', ');

writeFileSync(resolve(racineBackend, 'src/main/resources/db/demo/R__demo_annonces.sql'),
  `${entete('annonces (chaines de slides)')}
${upsert('annonces', ['id', 'titre', 'statut', 'publiee_le', 'version', 'cree_le', 'modifie_le'], annonces)}
-- Slides des annonces de demo : remplacees en bloc (ordre et contenu).
DELETE FROM \`annonce_slides\` WHERE \`annonce_id\` IN (${idsAnnonces});
INSERT INTO \`annonce_slides\` (\`id\`, \`annonce_id\`, \`position\`, \`titre\`, \`message\`, \`image_url\`) VALUES
${slides.map((l) => `  (${l.join(', ')})`).join(',\n')};
`);

// --- Zones de livraison ---------------------------------------------------------------

const maintenant = date('2026-11-13T00:00:00Z');
const regions = REGIONS.map((r) => [texte(r.code), texte(r.nom), bit(Boolean(zonesLivraison[r.code])), '0', maintenant]);
const secteurs = Object.entries(ARRONDISSEMENTS_PAR_REGION).flatMap(([code, liste]) => liste.map((a) => {
  const centre = CENTRES_ARRONDISSEMENTS[code]?.[a.nom];
  const prix = zonesLivraison[code]?.[a.nom] ?? PRIX_PAR_DEFAUT;
  return [texte(idSecteur(code, a.nom)), texte(code), texte(a.departement), texte(a.nom), entier(prix),
    nombre(centre?.latitude), nombre(centre?.longitude), bit(true), bit(false), '0', maintenant];
}));
const doublons = secteurs.map((s) => s[0]).filter((id, i, t) => t.indexOf(id) !== i);
if (doublons.length) throw new Error(`Identifiants de secteur en double : ${doublons.join(', ')}`);

writeFileSync(resolve(racineBackend, 'src/main/resources/db/demo/R__demo_zones.sql'),
  `${entete('regions du Senegal et arrondissements officiels (prix de demo du Manager)')}
${upsert('regions', ['code', 'nom', 'active', 'version', 'modifie_le'], regions, 'code')}
${upsert('secteurs', ['id', 'region_code', 'departement', 'nom', 'prix_livraison', 'centre_latitude', 'centre_longitude', 'est_officiel', 'est_masque', 'version', 'modifie_le'], secteurs)}`);

console.log(`Genere : ${categories.length} categories, ${produits.length} produits, ${plats.length} plats du jour, `
  + `${accompagnements.length} accompagnements/condiments, ${annonces.length} annonces (${slides.length} slides), `
  + `${regions.length} regions, ${secteurs.length} secteurs.`);
