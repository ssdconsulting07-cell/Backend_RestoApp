// Fichiers de la marque (dossier marque/, monte en /marque dans les deux apps) :
// logo, bannieres, photos de connexion, et icones generees (PWA, onglet).
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { extname, join } from 'node:path';
import sharp from 'sharp';
import { nomAsset } from './marque.mjs';

/** Couleur #RRGGBB -> { r, g, b } pour sharp. */
const rgb = (hex) => {
  const h = String(hex).replace('#', '');
  return { r: parseInt(h.slice(0, 2), 16), g: parseInt(h.slice(2, 4), 16), b: parseInt(h.slice(4, 6), 16), alpha: 1 };
};

/** Image source centree sur un carre de couleur (marge = part du cote laissee libre de chaque cote). */
async function icone(source, taille, fond, marge) {
  const interieur = Math.round(taille * (1 - 2 * marge));
  const motif = await sharp(source, { density: 384 })
    .resize(interieur, interieur, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png().toBuffer();
  return sharp({ create: { width: taille, height: taille, channels: 4, background: rgb(fond) } })
    .composite([{ input: motif, gravity: 'center' }]).png().toBuffer();
}

/** Photo recompressee : JPEG progressif, largeur max, sans metadonnees (EXIF, GPS). */
const photo = (source, largeurMax) => sharp(source).rotate()
  .resize({ width: largeurMax, withoutEnlargement: true })
  .flatten({ background: '#ffffff' }).jpeg({ quality: 82, progressive: true, mozjpeg: true }).toBuffer();

/**
 * Prepare marque/ dans `sortie` depuis le dossier du restaurant. Renvoie la
 * liste des fichiers ecrits (chemins relatifs a marque/).
 */
export async function preparerMarque(restaurant, dossier, sortie) {
  const a = restaurant.assets;
  const fond = restaurant.couleurs.principale;
  const ecrits = [];
  const ecrire = (nom, contenu) => {
    writeFileSync(join(sortie, nom), contenu);
    ecrits.push(nom);
  };
  mkdirSync(join(sortie, 'icons'), { recursive: true });

  // Logo et monogramme : tels quels (SVG net a toutes les tailles).
  for (const role of ['logo', 'monogramme']) {
    if (a[role]) {
      copyFileSync(join(dossier, a[role]), join(sortie, nomAsset(role, a[role])));
      ecrits.push(nomAsset(role, a[role]));
    }
  }
  // Photos : bannieres et ecran de connexion.
  if (a.banniere) ecrire(nomAsset('banniere', a.banniere), await photo(join(dossier, a.banniere), 1920));
  if (a.splash) ecrire(nomAsset('splash', a.splash), await photo(join(dossier, a.splash), 1080));
  for (const [i, rel] of (a.connexion || []).entries()) {
    ecrire(nomAsset('connexion', rel, i), await photo(join(dossier, rel), 1600));
  }

  // Icones de l'app installee (memes noms que public/icons des apps : elles les remplacent).
  const base = join(dossier, a.monogramme || a.logo);
  const fournies = a.iconesPwa || {};
  const icones = [
    ['icons/icon-192.png', fournies.icone192, 192, 0.08],
    ['icons/icon-512.png', fournies.icone512, 512, 0.08],
    ['icons/maskable-512.png', fournies.maskable512, 512, 0.2], // zone sure Android : 80 % central
    ['icons/apple-touch-icon.png', fournies.appleTouch, 180, 0.12],
  ];
  for (const [nom, fourni, taille, marge] of icones) {
    ecrire(nom, fourni ? readFileSync(join(dossier, fourni)) : await icone(base, taille, fond, marge));
  }

  // Favicon de l'onglet (SVG, remplace le monogramme genere) : le SVG fourni,
  // sinon une image PNG 64 px embarquee dans un SVG.
  const sourceFavicon = join(dossier, a.favicon || a.monogramme || a.logo);
  if (extname(sourceFavicon).toLowerCase() === '.svg') {
    ecrire('favicon.svg', readFileSync(sourceFavicon));
  } else {
    const png = await icone(sourceFavicon, 64, fond, 0.04);
    ecrire('favicon.svg', `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 64 64">`
      + `<image width="64" height="64" href="data:image/png;base64,${png.toString('base64')}"/></svg>\n`);
  }
  return ecrits;
}
