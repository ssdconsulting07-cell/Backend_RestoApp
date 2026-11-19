// Schema et controles : l'exemple est valide, et chaque erreur courante est
// signalee en francais, avec le fichier et le champ en cause.
import assert from 'node:assert/strict';
import { after, describe, it } from 'node:test';
import sharp from 'sharp';
import { formater, valider } from '../lib/validation.mjs';
import { copieExemple, EXEMPLE, nettoyer } from './aide.mjs';

after(nettoyer);

/** Valide et renvoie les erreurs formatees (une chaine par erreur). */
async function erreurs(dossier) {
  return (await valider(dossier)).erreurs.map(formater);
}

/** Verifie qu'une erreur contient tous les morceaux attendus. */
function contient(liste, ...morceaux) {
  assert.ok(
    liste.some((e) => morceaux.every((m) => e.includes(m))),
    `Erreur attendue contenant ${JSON.stringify(morceaux)}, obtenu :\n${liste.join('\n')}`,
  );
}

describe('clients/exemple', () => {
  it('est complet et valide, sans avertissement', async () => {
    const r = await valider(EXEMPLE);
    assert.deepEqual(r.erreurs.map(formater), []);
    assert.deepEqual(r.avertissements.map(formater), []);
    assert.equal(r.donnees.produits.length, 13);
  });
});

describe('erreurs claires en francais', () => {
  it('champ manquant', async () => {
    const c = copieExemple();
    c.modifier('restaurant.json', (r) => { delete r.slogan; });
    contient(await erreurs(c.dossier), 'restaurant.json', 'champ manquant : slogan');
  });

  it('couleur invalide', async () => {
    const c = copieExemple();
    c.modifier('restaurant.json', (r) => { r.couleurs.principale = 'vert'; });
    contient(await erreurs(c.dossier), 'couleurs › principale', 'couleur invalide', '#RRGGBB');
  });

  it('logo absent du fichier, puis du dossier', async () => {
    const c = copieExemple();
    c.modifier('restaurant.json', (r) => { delete r.assets.logo; });
    contient(await erreurs(c.dossier), 'logo absent');
    const d = copieExemple();
    d.supprimer('assets/logo.svg');
    contient(await erreurs(d.dossier), 'assets › logo', 'logo introuvable : assets/logo.svg');
  });

  it('logo trop petit', async () => {
    const c = copieExemple();
    await sharp({ create: { width: 100, height: 100, channels: 3, background: '#1F7A4D' } }).png()
      .toFile(`${c.dossier}/assets/petit.png`);
    c.modifier('restaurant.json', (r) => { r.assets.logo = 'assets/petit.png'; });
    contient(await erreurs(c.dossier), 'logo trop petit', '512 × 512');
  });

  it('prix negatif ou decimal', async () => {
    const c = copieExemple();
    c.modifier('donnees/produits.json', (p) => { p[0].prix = -500; p[1].prix = 3500.5; });
    const e = await erreurs(c.dossier);
    contient(e, 'donnees/produits.json', '« poisson-braise »', 'prix négatif');
    contient(e, '« brochettes-boeuf »', 'prix invalide', 'entier');
  });

  it('photo introuvable et photo trop lourde', async () => {
    const c = copieExemple();
    c.modifier('donnees/produits.json', (p) => { p[0].photo = 'donnees/photos/absente.jpg'; });
    contient(await erreurs(c.dossier), 'photo introuvable : donnees/photos/absente.jpg');
    const d = copieExemple();
    // Bruit aleatoire : JPEG impossible a compresser sous 2 Mo.
    const pixels = Buffer.alloc(1800 * 1800 * 3);
    for (let i = 0; i < pixels.length; i += 1) pixels[i] = Math.floor(Math.random() * 256);
    await sharp(pixels, { raw: { width: 1800, height: 1800, channels: 3 } }).jpeg({ quality: 100 })
      .toFile(`${d.dossier}/donnees/photos/lourde.jpg`);
    d.modifier('donnees/produits.json', (p) => { p[0].photo = 'donnees/photos/lourde.jpg'; });
    contient(await erreurs(d.dossier), 'photo trop lourde', '2 Mo maximum');
  });

  it('zone sans frais, zone inconnue sans departement, region inconnue', async () => {
    const c = copieExemple();
    c.modifier('restaurant.json', (r) => {
      delete r.livraison.zones[0].frais;
      r.livraison.zones.push({ region: 'THIES', secteur: 'Thies Nrd', frais: 500 });
      r.livraison.zones.push({ region: 'CASAMANCE', secteur: 'Ziguinchor', frais: 500 });
    });
    const e = await erreurs(c.dossier);
    contient(e, 'zones[0]', 'zone sans frais');
    contient(e, '« Thies Nrd » n\'est pas un arrondissement officiel de THIES', 'departement');
    contient(e, 'région inconnue « CASAMANCE »');
  });

  it('livraison proposee sans zones, aucun paiement en ligne', async () => {
    const c = copieExemple();
    c.modifier('restaurant.json', (r) => {
      delete r.livraison;
      r.paiements = { wave: false, orangeMoney: false, mixxByYas: false, carte: false, especesComptoir: true };
    });
    const e = await erreurs(c.dossier);
    contient(e, 'zones de livraison manquantes');
    contient(e, 'aucun moyen de paiement en ligne');
  });

  it('aucun mot de passe dans le dossier client', async () => {
    const c = copieExemple();
    c.modifier('donnees/personnel.json', (p) => { p[0].motDePasse = 'Secret-2026'; });
    c.modifier('restaurant.json', (r) => { r.deploiement.jwtSecret = 'abc'; });
    const e = await erreurs(c.dossier);
    contient(e, 'donnees/personnel.json', 'aucun mot de passe dans le dossier client');
    contient(e, 'restaurant.json', 'jwtSecret', 'secret interdit');
  });

  it('references croisees et doublons', async () => {
    const c = copieExemple();
    c.modifier('donnees/produits.json', (p) => { p[0].categorie = 'pizzas'; p[1].id = p[2].id; });
    c.modifier('donnees/personnel.json', (p) => { p[1].role = 'MANAGER'; });
    const e = await erreurs(c.dossier);
    contient(e, 'catégorie inconnue « pizzas »', 'grillades');
    contient(e, 'id en double');
    contient(e, 'un seul compte MANAGER');
  });

  it('JSON mal forme : ligne et colonne', async () => {
    const c = copieExemple();
    c.ecrire('donnees/categories.json', '[\n  { "id": "a", "nom": "A" },\n  { "id": "b" "nom": "B" }\n]');
    contient(await erreurs(c.dossier), 'donnees/categories.json', 'JSON invalide (ligne 3');
  });

  it('nomAccent qui n est pas la fin du nom', async () => {
    const c = copieExemple();
    c.modifier('restaurant.json', (r) => { r.nomAccent = 'Kër'; });
    contient(await erreurs(c.dossier), 'nomAccent', 'doit être la fin du nom');
  });
});
