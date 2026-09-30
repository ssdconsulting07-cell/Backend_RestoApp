package sn.chezketchup.backend.auth;

import sn.chezketchup.backend.security.Role;

public record LoginResponse(String token, Role role) {
}