package sn.chezketchup.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import sn.chezketchup.backend.security.Role;

public record StaffUserCreateRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Size(max = 240) String address,
        @NotBlank @Pattern(regexp = "^[0-9]{14}$", message = "La CNI doit contenir exactement 14 chiffres.") String cni,
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Size(min = 3, max = 80) String username,
        @NotNull Role role
) {
}