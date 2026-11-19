// Comptes initiaux du personnel : un mot de passe TEMPORAIRE aleatoire par
// compte, a changer a la premiere connexion (must_change_password).
//
// Les mots de passe en clair n'existent QUE dans deploiements/<id>/comptes-initiaux.txt
// (jamais commite, a remettre au restaurant puis supprimer). Le backend ne
// recoit que leur empreinte BCrypt (donnees/import.json). Relancer la
// commande reprend les mots de passe deja remis : ils restent valables.
import { randomInt } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import bcrypt from 'bcryptjs';

// Sans caracteres ambigus (0/O, 1/l/I) : le mot de passe se dicte et se recopie.
const ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';

export function motDePasseTemporaire(longueur = 14) {
  let mdp = '';
  for (let i = 0; i < longueur; i += 1) mdp += ALPHABET[randomInt(ALPHABET.length)];
  // Groupes de 4 a 5 : plus facile a lire (ex. Kx7m-Qp3a-Rt9z-Hh)
  return mdp.match(/.{1,5}/g).join('-');
}

/** Lit comptes-initiaux.txt : identifiant -> mot de passe deja remis. */
export function lireComptesExistants(fichier) {
  if (!existsSync(fichier)) return new Map();
  return new Map(readFileSync(fichier, 'utf8').split(/\r?\n/)
    .filter((l) => l && !l.startsWith('#'))
    .map((l) => l.split('\t'))
    .filter((c) => c.length >= 4 && c[0] !== 'identifiant')
    .map((c) => [c[0], c[3]]));
}

/**
 * Personnel du dossier -> { membres (pour import.json, avec empreinte), texte
 * (comptes-initiaux.txt) }.
 */
export function preparerComptes(personnel, restaurant, existants = new Map(), maintenant = new Date()) {
  const membres = [];
  const lignes = [];
  for (const m of personnel) {
    const identifiant = m.identifiant.toLowerCase();
    const mdp = existants.get(identifiant) || motDePasseTemporaire();
    membres.push({
      identifiant,
      role: m.role,
      prenom: m.prenom,
      nom: m.nom,
      telephone: m.telephone || null,
      motDePasseHash: bcrypt.hashSync(mdp, 10),
    });
    lignes.push([identifiant, m.role, `${m.prenom} ${m.nom}`, mdp].join('\t'));
  }
  const texte = [
    `# Comptes initiaux — ${restaurant.nom} — ${maintenant.toISOString().slice(0, 10)}`,
    '# CONFIDENTIEL : à remettre à chaque personne, en main propre ou par message privé, puis à supprimer.',
    '# Chaque mot de passe est TEMPORAIRE : il est demandé d\'en choisir un nouveau à la première connexion.',
    '# Ne jamais committer ce fichier.',
    '# Un compte déjà créé n\'est jamais modifié par un nouveau déploiement : si ce fichier a été supprimé puis',
    '# régénéré, réinitialisez son mot de passe depuis le Manager (page Personnel).',
    ['identifiant', 'rôle', 'nom', 'mot de passe temporaire'].join('\t'),
    ...lignes,
    '',
  ].join('\n');
  return { membres, texte };
}
