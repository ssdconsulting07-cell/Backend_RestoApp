package sn.chezketchup.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.Claims;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import sn.chezketchup.backend.auth.StaffUser;
import sn.chezketchup.backend.auth.StaffUserRepository;

/**
 * Lit le header Authorization: Bearer &lt;token&gt;, verifie le JWT et place le
 * role du staff dans le SecurityContext. Ignore silencieusement les requetes
 * sans token : elles restent anonymes (cas de l'App Client, qui n'en envoie jamais).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final StaffUserRepository staffUserRepository;

    public JwtAuthenticationFilter(JwtService jwtService, StaffUserRepository staffUserRepository) {
        this.jwtService = jwtService;
        this.staffUserRepository = staffUserRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parseClaims(header.substring(7));
                String username = claims.getSubject();
                StaffUser staffUser = staffUserRepository.findByUsernameIgnoreCase(username)
                    .filter(StaffUser::isActive)
                    .filter(user -> !user.mustChangePassword())
                    .orElseThrow();

                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + staffUser.getRole().name());
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(staffUser.getUsername(), null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
