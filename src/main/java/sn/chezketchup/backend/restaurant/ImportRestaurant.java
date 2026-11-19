package sn.chezketchup.backend.restaurant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.chezketchup.backend.annonces.application.AnnonceSaisie;
import sn.chezketchup.backend.annonces.application.AnnonceService;
import sn.chezketchup.backend.annonces.infrastructure.AnnonceRepository;
import sn.chezketchup.backend.auth.StaffUser;
import sn.chezketchup.backend.auth.StaffUserRepository;
import sn.chezketchup.backend.catalogue.application.GestionCatalogueService;
import sn.chezketchup.backend.catalogue.application.Horaires;
import sn.chezketchup.backend.catalogue.application.Saisies;
import sn.chezketchup.backend.catalogue.domaine.Disponibilite;
import sn.chezketchup.backend.catalogue.domaine.JourSemaine;
import sn.chezketchup.backend.catalogue.domaine.Service;
import sn.chezketchup.backend.catalogue.domaine.TypeAccompagnement;
import sn.chezketchup.backend.catalogue.infrastructure.AccompagnementCondimentRepository;
import sn.chezketchup.backend.catalogue.infrastructure.CategorieRepository;
import sn.chezketchup.backend.catalogue.infrastructure.PlatDuJourRepository;
import sn.chezketchup.backend.catalogue.infrastructure.ProduitRepository;
import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.publication.StatutPublication;
import sn.chezketchup.backend.photos.application.PhotoService;
import sn.chezketchup.backend.security.Role;
import sn.chezketchup.backend.zones.application.ZonesService;
import sn.chezketchup.backend.zones.application.ZonesVues;
import sn.chezketchup.backend.zones.domaine.Secteur;
import sn.chezketchup.backend.zones.infrastructure.RegionRepository;
import sn.chezketchup.backend.zones.infrastructure.SecteurRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Donnees initiales du restaurant, lues au demarrage dans le dossier genere
 * par l'outil-client (APP_RESTAURANT_IMPORT_DOSSIER, monte en lecture seule :
 * import.json + photos/). Meme image Docker pour tous les restaurants : rien
 * n'est compile, tout est lu ici.
 *
 * Regles (voir migration V9) :
 *   - un passage (jeton) ne s'applique qu'une fois : redemarrer ne refait rien ;
 *   - une fiche deja importee (meme cle) n'est jamais recreee ni ecrasee : le
 *     Gerant a pu la modifier, l'archiver ou la supprimer depuis le Manager ;
 *   - passage "forcer" (npm run client -- --forcer) : les fiches du fichier
 *     sont reimposees (recreees si supprimees). Les comptes du personnel ne
 *     sont JAMAIS modifies, meme force (mots de passe changes par leurs
 *     titulaires).
 *
 * Tout passe par les services metier (memes validations, memes caches
 * invalides, meme journal d'audit que le Manager) dans UNE transaction : une
 * erreur annule tout et le backend refuse de demarrer, avec un message clair.
 */
@Component
@Order(2)
public class ImportRestaurant implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ImportRestaurant.class);
    static final String FICHIER = "import.json";

    private final String dossier;
    private final ObjectMapper json;
    private final RegistreImport registre;
    private final GestionCatalogueService catalogue;
    private final Horaires horaires;
    private final AnnonceService annonces;
    private final ZonesService zones;
    private final PhotoService photos;
    private final CategorieRepository categories;
    private final ProduitRepository produits;
    private final AccompagnementCondimentRepository accompagnements;
    private final PlatDuJourRepository plats;
    private final AnnonceRepository annoncesRepo;
    private final RegionRepository regions;
    private final SecteurRepository secteurs;
    private final StaffUserRepository comptes;
    private final JournalActions journal;

    @SuppressWarnings("java:S107") // un collaborateur par type de fiche importee
    public ImportRestaurant(@Value("${app.restaurant.import.dossier:}") String dossier, ObjectMapper json,
                            RegistreImport registre, GestionCatalogueService catalogue, Horaires horaires,
                            AnnonceService annonces, ZonesService zones, PhotoService photos,
                            CategorieRepository categories, ProduitRepository produits,
                            AccompagnementCondimentRepository accompagnements, PlatDuJourRepository plats,
                            AnnonceRepository annoncesRepo, RegionRepository regions, SecteurRepository secteurs,
                            StaffUserRepository comptes, JournalActions journal) {
        this.dossier = dossier == null ? "" : dossier.trim();
        this.json = json;
        this.registre = registre;
        this.catalogue = catalogue;
        this.horaires = horaires;
        this.annonces = annonces;
        this.zones = zones;
        this.photos = photos;
        this.categories = categories;
        this.produits = produits;
        this.accompagnements = accompagnements;
        this.plats = plats;
        this.annoncesRepo = annoncesRepo;
        this.regions = regions;
        this.secteurs = secteurs;
        this.comptes = comptes;
        this.journal = journal;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (dossier.isEmpty()) {
            return;
        }
        Path racine = Path.of(dossier);
        Path fichier = racine.resolve(FICHIER);
        if (!Files.isRegularFile(fichier)) {
            LOG.warn("Import du restaurant : {} introuvable, rien a importer.", fichier);
            return;
        }
        importer(lire(fichier), racine);
    }

    DonneesImport lire(Path fichier) {
        try {
            return json.readValue(Files.readAllBytes(fichier), DonneesImport.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Import du restaurant : " + fichier + " illisible ("
                    + e.getMessage() + "). Regenerez-le avec npm run client.", e);
        }
    }

    /** Applique un passage ; renvoie le bilan (ou null si le passage etait deja applique). */
    @Transactional
    public Bilan importer(DonneesImport d, Path racine) {
        if (d.format() != DonneesImport.FORMAT) {
            throw new IllegalStateException("Import du restaurant : format " + d.format() + " inconnu (attendu "
                    + DonneesImport.FORMAT + "). Mettez a jour le backend ou regenerez le dossier.");
        }
        if (d.jeton() == null || d.jeton().isBlank()) {
            throw new IllegalStateException("Import du restaurant : jeton manquant dans import.json.");
        }
        if (registre.passageApplique(d.jeton())) {
            LOG.info("Import du restaurant {} : passage {} deja applique, rien a faire.", d.restaurant(), d.jeton());
            return null;
        }
        Passage p = new Passage(d, racine);
        try {
            p.horaires();
            Map<String, String> idsCategories = p.categories();
            p.produits(idsCategories);
            p.accompagnements();
            p.platsDuJour();
            p.annonces();
            p.zones();
            p.personnel();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Import du restaurant " + d.restaurant() + " echoue sur " + p.enCours
                    + " : " + e.getMessage(), e);
        }
        String bilan = p.bilan.toString();
        registre.enregistrerPassage(d.jeton(), d.restaurant() == null ? "?" : d.restaurant(), d.forcer(), bilan);
        journal.enregistrerEnTantQue("systeme", JournalActions.Domaine.PRODUIT, "IMPORT", d.jeton(),
                "a importé les données initiales du restaurant (" + bilan + ")");
        LOG.info("Import du restaurant {} ({}) : {}", d.restaurant(), d.forcer() ? "force" : "creation seule", bilan);
        return p.bilan;
    }

    /** Comptes de l'import : fiches creees, reimposees (forcer) et laissees telles quelles. */
    public static final class Bilan {
        int crees;
        int reimposes;
        int conserves;

        public int crees() {
            return crees;
        }

        public int reimposes() {
            return reimposes;
        }

        public int conserves() {
            return conserves;
        }

        @Override
        public String toString() {
            return crees + " créée(s), " + reimposes + " réimposée(s), " + conserves + " conservée(s)";
        }
    }

    /** Un passage d'import (etat local : bilan, fiche en cours pour les messages d'erreur). */
    private final class Passage {

        private final DonneesImport d;
        private final Path racine;
        private final Bilan bilan = new Bilan();
        private final Map<String, String> photosDeposees = new HashMap<>();
        private String enCours = "?";

        Passage(DonneesImport d, Path racine) {
            this.d = d;
            this.racine = racine;
        }

        /**
         * Regle commune a toutes les fiches : creer si la cle est inconnue
         * (ou si la fiche a disparu et qu'on force), reimposer si on force,
         * sinon ne rien toucher. Renvoie l'identifiant de la ressource.
         */
        private String fiche(String entite, String cle, Function<String, Boolean> existe, Supplier<String> creer,
                             Consumer<String> reimposer) {
            enCours = entite + " « " + cle + " »";
            Optional<String> connu = registre.ressource(entite, cle);
            if (connu.isPresent() && Boolean.TRUE.equals(existe.apply(connu.get()))) {
                if (d.forcer()) {
                    reimposer.accept(connu.get());
                    bilan.reimposes++;
                } else {
                    bilan.conserves++;
                }
                return connu.get();
            }
            if (connu.isPresent() && !d.forcer()) {
                // Importee puis supprimee par le Gerant : elle ne revient pas.
                bilan.conserves++;
                return connu.get();
            }
            String id = creer.get();
            registre.memoriser(entite, cle, id);
            bilan.crees++;
            return id;
        }

        private static Function<String, Boolean> existeLong(JpaRepository<?, Long> repo) {
            return id -> repo.existsById(Long.valueOf(id));
        }

        // --- Horaires ----------------------------------------------------------

        void horaires() {
            for (DonneesImport.Horaire h : d.horaires()) {
                Service service = Service.valueOf(h.service());
                Runnable appliquer = () -> horaires.modifier(List.of(new Horaires.Saisie(service, h.debut(), h.fin(), null)));
                // Les horaires existent deja (valeurs par defaut, V8) : la cle
                // inconnue signifie "jamais regles par l'import".
                fiche("horaire", service.name(), id -> true, () -> {
                    appliquer.run();
                    return service.name();
                }, id -> appliquer.run());
            }
        }

        // --- Catalogue ------------------------------------------------------------

        Map<String, String> categories() {
            Map<String, String> ids = new HashMap<>();
            for (DonneesImport.Categorie c : d.categories()) {
                Saisies.Categorie saisie = new Saisies.Categorie(c.nom(), c.accompagnementPropose(), null);
                String id = fiche("categorie", c.cle(), existeLong(categories), () -> {
                    // Categorie du meme nom creee a la main avant l'import : adoptee, pas dupliquee.
                    Optional<String> homonyme = categories.findAllByOrderByNomAsc().stream()
                            .filter(x -> x.getNom().equalsIgnoreCase(c.nom().trim()))
                            .map(x -> String.valueOf(x.getId())).findFirst();
                    if (homonyme.isPresent()) {
                        return homonyme.get();
                    }
                    String cree = catalogue.creerCategorie(saisie).id();
                    statut(StatutPublication.BROUILLON, c.statut(), s -> catalogue.changerStatutCategorie(
                            Long.parseLong(cree), new Saisies.ChangementStatut(s, null, null)));
                    return cree;
                }, existant -> {
                    var vue = catalogue.modifierCategorie(Long.parseLong(existant), saisie);
                    statut(vue.statut(), c.statut(), s -> catalogue.changerStatutCategorie(
                            Long.parseLong(existant), new Saisies.ChangementStatut(s, null, null)));
                });
                ids.put(c.cle(), id);
            }
            return ids;
        }

        void produits(Map<String, String> idsCategories) {
            for (DonneesImport.Produit p : d.produits()) {
                Supplier<Saisies.Produit> saisie = () -> {
                    String categorieId = idsCategories.get(p.categorie());
                    if (categorieId == null) {
                        throw new IllegalArgumentException("catégorie « " + p.categorie() + " » inconnue");
                    }
                    return new Saisies.Produit(p.nom(), p.description(), p.prix(), categorieId, photo(p.photo()),
                            p.disponibilite() == null ? Disponibilite.EN_STOCK : Disponibilite.valueOf(p.disponibilite()),
                            null, p.tendance() == null ? "AUTO" : p.tendance(), null);
                };
                fiche("produit", p.cle(), existeLong(produits), () -> {
                    String cree = catalogue.creerProduit(saisie.get()).id();
                    statut(StatutPublication.BROUILLON, p.statut(), s -> catalogue.changerStatutProduit(
                            Long.parseLong(cree), new Saisies.ChangementStatut(s, null, null)));
                    return cree;
                }, existant -> {
                    var vue = catalogue.modifierProduit(Long.parseLong(existant), saisie.get());
                    statut(vue.statut(), p.statut(), s -> catalogue.changerStatutProduit(
                            Long.parseLong(existant), new Saisies.ChangementStatut(s, null, null)));
                });
            }
        }

        void accompagnements() {
            for (DonneesImport.Accompagnement a : d.accompagnements()) {
                Saisies.AccompagnementCondiment saisie = new Saisies.AccompagnementCondiment(a.nom(), a.prix(),
                        TypeAccompagnement.valueOf(a.type()), null);
                fiche("accompagnement", a.cle(), existeLong(accompagnements), () -> {
                    String cree = catalogue.creerAccompagnement(saisie).id();
                    statut(StatutPublication.BROUILLON, a.statut(), s -> catalogue.changerStatutAccompagnement(
                            Long.parseLong(cree), new Saisies.ChangementStatut(s, null, null)));
                    return cree;
                }, existant -> {
                    var vue = catalogue.modifierAccompagnement(Long.parseLong(existant), saisie);
                    statut(vue.statut(), a.statut(), s -> catalogue.changerStatutAccompagnement(
                            Long.parseLong(existant), new Saisies.ChangementStatut(s, null, null)));
                });
            }
        }

        void platsDuJour() {
            for (DonneesImport.PlatDuJour p : d.platsDuJour()) {
                Supplier<Saisies.PlatDuJour> saisie = () -> new Saisies.PlatDuJour(p.nom(), p.description(), p.prix(),
                        photo(p.photo()), null,
                        (p.jours() == null ? List.<String>of() : p.jours()).stream().map(JourSemaine::valueOf).toList(),
                        p.dejeuner(), p.diner(), null);
                fiche("plat-du-jour", p.cle(), existeLong(plats), () -> {
                    String cree = catalogue.creerPlat(saisie.get()).id();
                    statut(StatutPublication.BROUILLON, p.statut(), s -> catalogue.changerStatutPlat(
                            Long.parseLong(cree), new Saisies.ChangementStatut(s, null, null)));
                    return cree;
                }, existant -> {
                    var vue = catalogue.modifierPlat(Long.parseLong(existant), saisie.get());
                    statut(vue.statut(), p.statut(), s -> catalogue.changerStatutPlat(
                            Long.parseLong(existant), new Saisies.ChangementStatut(s, null, null)));
                });
            }
        }

        // --- Annonces ---------------------------------------------------------------

        void annonces() {
            for (DonneesImport.Annonce a : d.annonces()) {
                Supplier<AnnonceSaisie> saisie = () -> new AnnonceSaisie(a.titre(), a.slides().stream()
                        .map(s -> new AnnonceSaisie.Slide(s.titre(), s.message(), photo(s.image()))).toList(), null);
                fiche("annonce", a.cle(), existeLong(annoncesRepo), () -> {
                    String cree = annonces.creer(saisie.get()).id();
                    statut(StatutPublication.BROUILLON, a.statut(),
                            s -> annonces.changerStatut(Long.parseLong(cree), s, null));
                    return cree;
                }, existant -> {
                    var vue = annonces.modifier(Long.parseLong(existant), saisie.get());
                    statut(vue.statut(), a.statut(), s -> annonces.changerStatut(Long.parseLong(existant), s, null));
                });
            }
        }

        // --- Zones de livraison --------------------------------------------------------

        void zones() {
            for (DonneesImport.Region r : d.zones()) {
                enCours = "région « " + r.code() + " »";
                if (!regions.existsById(r.code())) {
                    throw new IllegalArgumentException("région inconnue (codes : DAKAR, THIES, SAINT_LOUIS...)");
                }
                boolean premiereFois = registre.ressource("region", r.code()).isEmpty();
                fiche("region", r.code(), id -> true, () -> {
                    zones.activerRegion(r.code(), new ZonesVues.ActivationRegion(true, null));
                    return r.code();
                }, id -> zones.activerRegion(r.code(), new ZonesVues.ActivationRegion(true, null)));

                Set<String> listes = new HashSet<>();
                for (DonneesImport.Secteur s : r.secteurs()) {
                    listes.add(secteur(r.code(), s));
                }
                if (r.masquerAutres() && (premiereFois || d.forcer())) {
                    for (Secteur officiel : secteurs.findByRegionCodeOrderByDepartementAscNomAsc(r.code())) {
                        if (officiel.isEstOfficiel() && !listes.contains(officiel.getId()) && !officiel.isEstMasque()) {
                            zones.masquerSecteur(officiel.getId(), new ZonesVues.MasquageSecteur(true, null));
                        }
                    }
                }
            }
        }

        private String secteur(String region, DonneesImport.Secteur s) {
            if (s.officiel() != null && !s.officiel().isBlank()) {
                String id = s.officiel();
                Runnable appliquer = () -> {
                    zones.modifierSecteur(id, new ZonesVues.ModificationSecteur(null, null, s.frais(), null));
                    if (secteurs.findById(id).map(Secteur::isEstMasque).orElse(false)) {
                        zones.masquerSecteur(id, new ZonesVues.MasquageSecteur(false, null));
                    }
                };
                if (!secteurs.existsById(id)) {
                    enCours = "zone « " + id + " »";
                    throw new IllegalArgumentException("arrondissement officiel inconnu");
                }
                return fiche("secteur", id, x -> true, () -> {
                    appliquer.run();
                    return id;
                }, x -> appliquer.run());
            }
            String id = Secteur.identifiant(region, s.nom());
            return fiche("secteur", id, secteurs::existsById, () -> {
                if (secteurs.existsById(id)) {
                    // Zone deja ajoutee a la main sous ce nom : seul le prix est aligne.
                    zones.modifierSecteur(id, new ZonesVues.ModificationSecteur(null, null, s.frais(), null));
                    return id;
                }
                return zones.ajouterSecteur(region, new ZonesVues.NouveauSecteur(s.departement(), s.nom(), s.frais())).id();
            }, x -> zones.modifierSecteur(id, new ZonesVues.ModificationSecteur(s.departement(), s.nom(), s.frais(), null)));
        }

        // --- Personnel -------------------------------------------------------------------

        void personnel() {
            for (DonneesImport.Membre m : d.personnel()) {
                String identifiant = m.identifiant().trim().toLowerCase(Locale.ROOT);
                enCours = "compte « " + identifiant + " »";
                Role role = Role.valueOf(m.role());
                if (m.motDePasseHash() == null || !m.motDePasseHash().matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
                    throw new IllegalArgumentException("mot de passe temporaire absent ou non haché (BCrypt attendu)");
                }
                Optional<String> connu = registre.ressource("personnel", identifiant);
                if (connu.isPresent() || comptes.existsByUsernameIgnoreCase(identifiant)) {
                    // Jamais modifie, meme force : son titulaire a pu changer son mot de passe.
                    bilan.conserves++;
                    continue;
                }
                if (role == Role.MANAGER && comptes.existsByRole(Role.MANAGER)) {
                    LOG.warn("Import : un compte MANAGER existe deja, {} n'est pas cree.", identifiant);
                    bilan.conserves++;
                    continue;
                }
                StaffUser u = new StaffUser(identifiant, m.motDePasseHash(), role, true);
                u.setFirstName(m.prenom());
                u.setLastName(m.nom());
                u.setPhone(m.telephone());
                StaffUser cree = comptes.save(u);
                registre.memoriser("personnel", identifiant, String.valueOf(cree.getId()));
                journal.enregistrerEnTantQue("systeme", JournalActions.Domaine.PERSONNEL, "CREATION",
                        String.valueOf(cree.getId()), "a créé le compte " + role.name().toLowerCase(Locale.ROOT)
                                + " « " + identifiant + " » (mot de passe à changer à la première connexion)");
                bilan.crees++;
            }
        }

        // --- Utilitaires ------------------------------------------------------------------

        /** Photo du dossier d'import deposee comme un envoi du Manager (nettoyee, nom aleatoire). */
        private String photo(String source) {
            if (source == null || source.isBlank()) {
                return null;
            }
            if (source.startsWith("https://") || source.startsWith("/")) {
                return source;
            }
            return photosDeposees.computeIfAbsent(source, chemin -> {
                Path f = racine.resolve(chemin).normalize();
                if (!f.startsWith(racine.normalize()) || !Files.isRegularFile(f)) {
                    throw new IllegalArgumentException("photo introuvable : " + chemin);
                }
                try {
                    return photos.deposer(Files.readAllBytes(f)).url();
                } catch (IOException e) {
                    throw new IllegalArgumentException("photo illisible : " + chemin, e);
                }
            });
        }
    }

    /** Statut cible du fichier (BROUILLON par defaut), en passant par BROUILLON si la transition l'exige. */
    static void statut(StatutPublication actuel, String voulu, Consumer<StatutPublication> changer) {
        StatutPublication cible = voulu == null ? StatutPublication.BROUILLON : StatutPublication.valueOf(voulu);
        for (StatutPublication etape : chemin(actuel, cible)) {
            changer.accept(etape);
        }
    }

    static List<StatutPublication> chemin(StatutPublication de, StatutPublication vers) {
        List<StatutPublication> etapes = new ArrayList<>();
        if (de == vers) {
            return etapes;
        }
        if (!de.peutPasserA(vers)) {
            etapes.add(StatutPublication.BROUILLON);
        }
        etapes.add(vers);
        return etapes;
    }
}
