package sn.chezketchup.backend.auth;

public record TemporaryPasswordResponse(StaffUserResponse user, String temporaryPassword) {
}