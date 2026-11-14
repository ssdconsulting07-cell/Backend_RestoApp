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
        boolean mustChangePassword,
        // Livreurs : note moyenne des clients (1 a 5, une decimale) et nombre
        // d'avis ; null / 0 pour les autres roles ou sans avis.
        Double noteMoyenne,
        long nombreAvis
) {
    public static StaffUserResponse from(StaffUser user) {
        return from(user, null, 0);
    }

    public static StaffUserResponse from(StaffUser user, Double noteMoyenne, long nombreAvis) {
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
                user.mustChangePassword(),
                noteMoyenne,
                nombreAvis
        );
    }
}