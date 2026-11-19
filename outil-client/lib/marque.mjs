// restaurant.json -> variables d'environnement lues AU DEMARRAGE des
// conteneurs (memes images pour tous les restaurants) :
//   MARQUE_* / SUPPORT_*  -> App Client et Manager (docker/40-config-runtime.sh -> /config.js)
//   APP_RESTAURANT_*      -> backend (moyens de paiement et modes refuses cote serveur)
// En developpement, les memes valeurs prefixees VITE_ (fichiers .env.local).
import { extname } from 'node:path';

/** Codes communs aux apps et au backend (MoyenPaiement.java, modesPaiement.jsx). */
export const CODES_PAIEMENT = {
  wave: 'wave',
  orangeMoney: 'orange-money',
  mixxByYas: 'mixx-yas',
  carte: 'carte',
  especesComptoir: 'especes',
};

const JOURS = ['lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi', 'dimanche'];

/** Nom publie d'un asset dans le dossier marque/ (servi en /marque/...). */
export function nomAsset(role, source, index) {
  const ext = extname(source).toLowerCase();
  // Les photos sont recompressees en JPEG (poids), les logos gardent leur format.
  const sortie = ['banniere', 'splash', 'connexion'].includes(role) ? '.jpg' : (ext === '.jpeg' ? '.jpg' : ext);
  return `${role}${index === undefined ? '' : `-${index + 1}`}${sortie}`;
}

/** "https://commande.chezfatou.sn/" -> "commande.chezfatou.sn" */
const domaine = (url) => String(url || '').replace(/^https:\/\//, '').replace(/\/$/, '');

/** Variables de la marque et du deploiement (aucun secret). Ordre stable. */
export function variables(restaurant) {
  const r = restaurant;
  const a = r.assets || {};
  const moyens = Object.entries(CODES_PAIEMENT).filter(([cle]) => r.paiements?.[cle]).map(([, code]) => code);
  const modes = [r.modes?.livraison && 'livraison', r.modes?.retraitComptoir && 'retrait'].filter(Boolean);
  const ouverture = (r.horaires?.ouverture || []).map((o) => ({
    jours: [...o.jours].sort((x, y) => JOURS.indexOf(x) - JOURS.indexOf(y)), debut: o.debut, fin: o.fin,
  }));
  const url = (role, i) => {
    const source = i === undefined ? a[role] : a[role][i];
    return source ? `/marque/${nomAsset(role, source, i)}` : '';
  };
  return {
    // Deploiement (Caddy, images communes)
    COMPOSE_PROJECT_NAME: `resto-${r.identifiant}`,
    DOMAINE_CLIENT: domaine(r.deploiement?.urlClient),
    DOMAINE_MANAGER: domaine(r.deploiement?.urlManager),
    EMAIL_CERTIFICATS: r.deploiement?.emailCertificats || '',
    VERSION: r.deploiement?.versionImages || 'latest',
    // Support (bouton « Besoin d'aide ? »)
    SUPPORT_TELEPHONE: r.coordonnees?.telephone || '',
    SUPPORT_EMAIL: r.coordonnees?.emailSupport || '',
    // Marque (App Client et Manager)
    MARQUE_NOM: r.nom,
    MARQUE_NOM_COURT: r.nomCourt || '',
    MARQUE_NOM_ACCENT: r.nomAccent || '',
    MARQUE_INITIALES: r.initiales || '',
    MARQUE_SLOGAN: r.slogan || '',
    MARQUE_SOUS_TITRE: r.sousTitre || '',
    MARQUE_DESCRIPTION: r.description || '',
    MARQUE_ETABLISSEMENT: r.etablissement || '',
    MARQUE_COULEUR: (r.couleurs?.principale || '').toUpperCase(),
    MARQUE_COULEUR_FONCEE: (r.couleurs?.foncee || '').toUpperCase(),
    MARQUE_COULEUR_CLAIRE: (r.couleurs?.claire || '').toUpperCase(),
    MARQUE_LOGO_URL: url('logo'),
    MARQUE_BANNIERE_URL: url('banniere'),
    MARQUE_SPLASH_URL: url('splash'),
    MARQUE_CONNEXION_IMAGES: (a.connexion || []).map((_, i) => url('connexion', i)).join(','),
    MARQUE_ACCUEIL_BADGE: r.accueil?.badge || '',
    MARQUE_ACCUEIL_TEXTE: r.accueil?.texte || '',
    MARQUE_ADRESSE: r.coordonnees?.adresse || '',
    MARQUE_TELEPHONE: r.coordonnees?.telephone || '',
    MARQUE_RESEAUX: r.coordonnees?.reseauxSociaux ? JSON.stringify(r.coordonnees.reseauxSociaux) : '',
    MARQUE_HORAIRES: ouverture.length ? JSON.stringify(ouverture) : '',
    MARQUE_PAIEMENTS: moyens.join(','),
    MARQUE_MODES: modes.join(','),
    // Backend : refuse ce qui n'est pas propose
    APP_RESTAURANT_MOYENS_PAIEMENT: moyens.join(','),
    APP_RESTAURANT_MODES: modes.join(','),
  };
}

/** Variables utiles aux apps en `npm run dev` (prefixe VITE_), sans celles du deploiement. */
export function variablesVite(vars) {
  return Object.fromEntries(Object.entries(vars)
    .filter(([k]) => k.startsWith('MARQUE_') || k.startsWith('SUPPORT_'))
    .map(([k, v]) => [`VITE_${k}`, v]));
}

/**
 * Valeur ecrite dans un .env lu par docker compose : entre apostrophes
 * (litteral, aucune interpolation) ; si elle contient une apostrophe, entre
 * guillemets avec \ " et $ echappes.
 */
export function valeurEnv(v) {
  const s = String(v ?? '').replace(/\r?\n/g, ' ');
  if (!s.includes("'")) return `'${s}'`;
  return `"${s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '$$$$')}"`;
}

export function fichierEnv(vars, entete) {
  return `${entete}\n${Object.entries(vars).map(([k, v]) => `${k}=${valeurEnv(v)}`).join('\n')}\n`;
}
