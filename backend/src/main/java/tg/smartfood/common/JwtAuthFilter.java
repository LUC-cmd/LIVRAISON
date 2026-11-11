package tg.smartfood.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tg.smartfood.user.UserRepository;
import tg.smartfood.user.UserStatus;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            // Le compte est relu en base a chaque requete : une suspension ou un changement de role prend effet immediatement.
            jwtService.parse(header.substring(7))
                    .flatMap(token -> users.findById(token.id()))
                    .filter(account -> account.getStatus() == UserStatus.ACTIVE)
                    .map(account -> new AuthUser(account.getId(), account.getEmail(), Set.copyOf(account.getRoles())))
                    .ifPresent(user -> {
                List<SimpleGrantedAuthority> authorities = user.roles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                        .toList();
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
            });
        }
        chain.doFilter(request, response);
    }
}
