package sn.chezketchup.backend.auth;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.audit.JournalActions.Domaine;
import sn.chezketchup.backend.security.Role;

@Service
public class StaffUserManagementService {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final JournalActions journal;

    public StaffUserManagementService(
            StaffUserRepository staffUserRepository,
            PasswordEncoder passwordEncoder,
            TemporaryPasswordGenerator temporaryPasswordGenerator,
            JournalActions journal
    ) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.journal = journal;
    }

    @Transactional(readOnly = true)
    public List<StaffUserResponse> list() {
        return staffUserRepository.findAllByOrderByUsernameAsc().stream()
                .map(StaffUserResponse::from)
                .toList();
    }

    @Transactional
    public TemporaryPasswordResponse create(StaffUserCreateRequest request) {
        String username = normalizeUsername(request.username());
        ensureUsernameAvailable(username, null);
        // Le compte Manager est unique et cree par le systeme : on refuse de
        // creer un MANAGER ici. (Avant : "|| existsByRole(MANAGER)" refusait
        // TOUTE creation de compte des qu'un Manager existait, donc toujours.)
        if (request.role() == Role.MANAGER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le compte Manager est unique et gere par le systeme.");
        }

        String temporaryPassword = temporaryPasswordGenerator.generate();
        StaffUser user = new StaffUser(
                username,
                passwordEncoder.encode(temporaryPassword),
                request.role(),
                true
        );
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setAddress(request.address().trim());
        user.setCni(request.cni().trim());
        user.setPhone(request.phone().trim());
        user = staffUserRepository.save(user);
        journal.enregistrer(Domaine.PERSONNEL, "CREATION_COMPTE", String.valueOf(user.getId()),
                "a créé le compte " + libelleRole(user.getRole()) + " de " + nom(user));
        return new TemporaryPasswordResponse(StaffUserResponse.from(user), temporaryPassword);
    }

    @Transactional
    public StaffUserResponse update(long id, StaffUserUpdateRequest request, String currentUsername) {
        StaffUser user = getUser(id);
        ensureManagerAccountIsProtected(user);
        if (request.role() == Role.MANAGER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le role Manager ne peut pas etre attribue depuis cette page.");
        }
        String username = normalizeUsername(request.username());
        ensureCurrentGerantIdentityUnchanged(user, username, request.role(), currentUsername);
        ensureUsernameAvailable(username, id);
        ensureGerantRemains(user, request.role(), request.active());
        ensureNotDeactivatingCurrentUser(user, request.active(), currentUsername);

        user.setUsername(username);
        user.setRole(request.role());
        user.setActive(request.active());
        if (request.firstName() != null) user.setFirstName(request.firstName().trim());
        if (request.lastName() != null) user.setLastName(request.lastName().trim());
        if (request.address() != null) user.setAddress(request.address().trim());
        if (request.cni() != null) user.setCni(request.cni().trim());
        if (request.phone() != null) user.setPhone(request.phone().trim());
        journal.enregistrer(Domaine.PERSONNEL, "MODIFICATION_COMPTE", String.valueOf(id), "a modifié le compte de " + nom(user));
        return StaffUserResponse.from(staffUserRepository.save(user));
    }

    @Transactional
    public void deactivate(long id, String currentUsername) {
        StaffUser user = getUser(id);
        ensureManagerAccountIsProtected(user);
        ensureNotDeactivatingCurrentUser(user, false, currentUsername);
        ensureGerantRemains(user, user.getRole(), false);
        user.setActive(false);
        staffUserRepository.save(user);
        journal.enregistrer(Domaine.PERSONNEL, "DESACTIVATION_COMPTE", String.valueOf(id), "a désactivé le compte de " + nom(user));
    }

    @Transactional
    public void deletePermanently(long id, String currentUsername) {
        StaffUser user = getUser(id);
        ensureManagerAccountIsProtected(user);
        if (user.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Desactivez le compte avant de le supprimer definitivement.");
        }
        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous ne pouvez pas supprimer votre propre compte.");
        }
        staffUserRepository.delete(user);
        journal.enregistrer(Domaine.PERSONNEL, "SUPPRESSION_COMPTE", String.valueOf(id),
                "a supprimé définitivement le compte de " + nom(user));
    }

    @Transactional
    public TemporaryPasswordResponse regeneratePassword(long id, String currentUsername) {
        StaffUser user = getUser(id);
        ensureManagerAccountIsProtected(user);
        ensureNotRegeneratingCurrentUser(user, currentUsername);
        String temporaryPassword = temporaryPasswordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        StaffUser saved = staffUserRepository.save(user);
        journal.enregistrer(Domaine.PERSONNEL, "MOT_DE_PASSE_TEMPORAIRE", String.valueOf(id),
                "a généré un mot de passe temporaire pour " + nom(user));
        return new TemporaryPasswordResponse(StaffUserResponse.from(saved), temporaryPassword);
    }

    /** Nom affiche dans le journal (jamais le mot de passe, ni la CNI, ni le telephone). */
    private static String nom(StaffUser u) {
        String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return n.isEmpty() ? u.getUsername() : n;
    }

    private static String libelleRole(Role role) {
        return switch (role) {
            case CUISINE -> "cuisine";
            case GERANT -> "gérant";
            case MANAGER -> "manager";
            case LIVREUR -> "livreur";
            case VENDEUR -> "vendeur";
        };
    }

    private StaffUser getUser(long id) {
        return staffUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
    }

    private void ensureManagerAccountIsProtected(StaffUser user) {
        if (user.getRole() == Role.MANAGER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le compte Manager est unique et protege.");
        }
    }

    private void ensureUsernameAvailable(String username, Long exceptId) {
        staffUserRepository.findByUsernameIgnoreCase(username)
                .filter(user -> !user.getId().equals(exceptId))
                .ifPresent(user -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet identifiant est deja utilise.");
                });
    }

    private void ensureGerantRemains(StaffUser user, Role nextRole, boolean nextActive) {
        if (user.isActive() && user.getRole() == Role.GERANT
                && (!nextActive || nextRole != Role.GERANT)
                && staffUserRepository.countByRoleAndActiveTrue(Role.GERANT) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le dernier compte Gerant actif ne peut pas etre retire.");
        }
    }

    private void ensureNotDeactivatingCurrentUser(StaffUser user, boolean nextActive, String currentUsername) {
        if (!nextActive && user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous ne pouvez pas desactiver votre propre compte.");
        }
    }

    private void ensureCurrentGerantIdentityUnchanged(
            StaffUser user,
            String nextUsername,
            Role nextRole,
            String currentUsername
    ) {
        if (user.getUsername().equalsIgnoreCase(currentUsername)
                && (!user.getUsername().equalsIgnoreCase(nextUsername) || user.getRole() != nextRole)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demandez a un autre Gerant de modifier votre compte.");
        }
    }

    private void ensureNotRegeneratingCurrentUser(StaffUser user, String currentUsername) {
        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Demandez a un autre Gerant de regenerer votre mot de passe.");
        }
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}