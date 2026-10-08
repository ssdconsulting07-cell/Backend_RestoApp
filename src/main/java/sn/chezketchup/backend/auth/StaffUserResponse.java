package sn.chezketchup.backend.auth;

import sn.chezketchup.backend.security.Role;

public record StaffUserResponse(
        Long id,
        String firstName,
        String lastName,
        String address,
        String cni,
        String phone,
        String username,
        Role role,
        boolean active,
        boolean mustChangePassword
) {
    public static StaffUserResponse from(StaffUser user) {
        return new StaffUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getAddress(),
                user.getCni(),
                user.getPhone(),
                user.getUsername(),
                user.getRole(),
                user.isActive(),
                user.mustChangePassword()
        );
    }
}