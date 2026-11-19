// Erreurs du validateur JSON Schema (ajv) traduites en phrases francaises,
// avec un chemin lisible : "produits[2] « yassa » › prix".

const TYPES = {
  string: 'un texte entre guillemets',
  integer: 'un nombre entier',
  number: 'un nombre',
  boolean: 'true ou false',
  array: 'une liste [ ... ]',
  object: 'un objet { ... }',
};

/** "/produits/2/prix" (+ donnees) -> "[2] « yassa » › prix" ; racine -> "". */
export function cheminLisible(instancePath, donnees) {
  if (!instancePath) return '';
  const morceaux = instancePath.split('/').slice(1).map((m) => m.replace(/~1/g, '/').replace(/~0/g, '~'));
  let courant = donnees;
  let texte = '';
  for (const m of morceaux) {
    if (Array.isArray(courant) && /^\d+$/.test(m)) {
      courant = courant[Number(m)];
      const id = courant && typeof courant === 'object' && (courant.id || courant.identifiant || courant.secteur);
      texte += `[${m}]${id ? ` « ${id} »` : ''}`;
    } else {
      courant = courant && typeof courant === 'object' ? courant[m] : undefined;
      texte += `${texte ? ' › ' : ''}${m}`;
    }
  }
  return texte;
}

function messageGenerique(e) {
  const p = e.params || {};
  switch (e.keyword) {
    case 'required': return `champ manquant : ${p.missingProperty}`;
    case 'additionalProperties': return `champ inconnu : « ${p.additionalProperty} » (faute de frappe ?)`;
    case 'type': return `${TYPES[p.type] || p.type} attendu`;
    case 'enum': return `valeur non autorisée (possibles : ${(p.allowedValues || []).map((v) => JSON.stringify(v)).join(', ')})`;
    case 'minItems': return `au moins ${p.limit} élément(s) attendu(s)`;
    case 'maxItems': return `${p.limit} élément(s) maximum`;
    case 'minLength': return p.limit <= 1 ? 'texte vide' : `${p.limit} caractères minimum`;
    case 'maxLength': return `${p.limit} caractères maximum`;
    case 'uniqueItems': return 'valeur en double dans la liste';
    case 'format': return p.format === 'email' ? 'adresse e-mail invalide' : `format ${p.format} invalide`;
    case 'pattern': return 'format invalide';
    case 'minimum': return `${p.limit} minimum`;
    case 'maximum': return `${p.limit} maximum`;
    default: return e.message || 'valeur invalide';
  }
}

/** Erreurs ajv -> [{ chemin, message }] (doublons retires). */
export function traduire(erreurs, donnees) {
  const vues = new Set();
  const sortie = [];
  for (const e of erreurs || []) {
    let chemin = e.instancePath;
    let message;
    if (e.keyword === 'errorMessage') {
      message = e.message;
      // Message personnalise pose sur l'objet parent : on precise le champ en cause.
      const origine = (e.params?.errors || [])[0];
      if (origine?.keyword === 'additionalProperties') {
        message = `${message} (« ${origine.params.additionalProperty} »)`;
      }
    } else {
      message = messageGenerique(e);
    }
    const ligne = { chemin: cheminLisible(chemin, donnees), message };
    const cle = `${ligne.chemin}|${ligne.message}`;
    if (!vues.has(cle)) {
      vues.add(cle);
      sortie.push(ligne);
    }
  }
  return sortie;
}
