package com.alaeldin.Auth_service.filter;

import com.alaeldin.Auth_service.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final WebAuthenticationDetailsSource authDetailsSource = new WebAuthenticationDetailsSource();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            if (!jwtService.validateToken(token)) {
                log.warn("[JwtAuthenticationFilter] Invalid JWT token path={}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return; // FIX: stop processing — do not fall through to authentication logic
            }

            final String username = jwtService.extractUsername(token);

            if (StringUtils.hasText(username) && SecurityContextHolder.getContext().getAuthentication() == null) {
                final String role = jwtService.extractRole(token);
                final List<String> permissions = jwtService.extractPermissions(token);

                // Build GrantedAuthority list:
                // 1. Role authority:       "ROLE_ADMIN", "ROLE_USER", etc.
                // 2. Permission authority: "ACCOUNT:READ", "TRANSACTION:EXECUTE", etc.
                // FIX: use mutable ArrayList so we can prepend the role authority
                List<SimpleGrantedAuthority> authorities = new ArrayList<>(
                        permissions.stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList());

                if (StringUtils.hasText(role)) {
                    authorities.add(0, new SimpleGrantedAuthority("ROLE_" + role));
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(authDetailsSource.buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("[JwtAuthenticationFilter] Authenticated user={} role={} permissions={}",
                        username, role, permissions.size());
            }

        } catch (Exception ex) {
            log.warn("[JwtAuthenticationFilter] Token processing failed path={} error={}",
                    request.getRequestURI(), ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
