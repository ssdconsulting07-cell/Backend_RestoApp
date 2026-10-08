package sn.chezketchup.backend.auth;

import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/staff-users")
@PreAuthorize("hasRole('GERANT')")
public class StaffUserController {

    private final StaffUserManagementService staffUserManagementService;

    public StaffUserController(StaffUserManagementService staffUserManagementService) {
        this.staffUserManagementService = staffUserManagementService;
    }

    @GetMapping
    public List<StaffUserResponse> list() {
        return staffUserManagementService.list();
    }

    @PostMapping
    public ResponseEntity<TemporaryPasswordResponse> create(@Valid @RequestBody StaffUserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffUserManagementService.create(request));
    }

    @PatchMapping("/{id}")
    public StaffUserResponse update(
            @PathVariable long id,
            @Valid @RequestBody StaffUserUpdateRequest request,
            Principal principal
    ) {
        return staffUserManagementService.update(id, request, principal.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable long id, Principal principal) {
        staffUserManagementService.deactivate(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable long id, Principal principal) {
        staffUserManagementService.deletePermanently(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/temporary-password")
    public TemporaryPasswordResponse regeneratePassword(@PathVariable long id, Principal principal) {
        return staffUserManagementService.regeneratePassword(id, principal.getName());
    }
}