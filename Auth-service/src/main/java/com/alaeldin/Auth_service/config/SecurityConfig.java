package com.alaeldin.Auth_service.config;

import com.alaeldin.Auth_service.constant.ResourceName;
import com.alaeldin.Auth_service.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration for the Auth Service.
 *
 * <p>Security model overview:</p>
 * <ul>
 *   <li>Stateless — no HTTP session is ever created ({@link SessionCreationPolicy#STATELESS}).</li>
 *   <li>JWT-based — every request is authenticated by {@link JwtAuthenticationFilter}
 *       before reaching any controller.</li>
 *   <li>Method security — fine-grained {@code @PreAuthorize} guards on the service layer
 *       are enabled via {@link EnableMethodSecurity}. The URL-level rules here are a
 *       coarse first gate; the service layer is the authoritative access-control boundary.</li>
 *   <li>Consistent error responses — {@link AuthEntryPoint} (401) and
 *       {@link AuthAccessDeniedHandler} (403) return JSON matching the project's
 *       {@link com.alaeldin.Auth_service.exception.ApiErrorResponse} shape.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService      userDetailsService;
    private final AuthEntryPoint          authEntryPoint;
    private final AuthAccessDeniedHandler accessDeniedHandler;

    // ─────────────────────────────────────────────────────────────
    //  Security filter chain
    // ─────────────────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── Disable CSRF — stateless JWT API has no session to protect ──────
            .csrf(AbstractHttpConfigurer::disable)

            // ── No HTTP session — every request must carry its own JWT ───────────
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── URL-level access rules ────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth

                // Public auth endpoints — no token required
                .requestMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh"
                ).permitAll()

                // Diagnostic endpoint - TEMPORARY, REMOVE IN PRODUCTION
                .requestMatchers("/api/v1/diagnostic/**").permitAll()

                // Actuator probes — allow infra checks without a token
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/info"
                ).permitAll()

                // Admin endpoints — coarse URL gate; fine-grained authority checks
                // (ADMIN_PANEL:READ / ADMIN_PANEL:WRITE) are enforced by @PreAuthorize
                // on each AdminService method.
                .requestMatchers(ResourceName.ADMIN_PANEL.getApiPath())
                        .hasAuthority("ADMIN_PANEL:READ")

                // Every other endpoint requires a valid JWT
                .anyRequest().authenticated()
            )

            // ── JSON error responses (replaces Spring's default HTML whitepage) ──
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))

            // ── Wire the authentication provider ─────────────────────────────────
            .authenticationProvider(authenticationProvider())

            // ── JWT filter runs before Spring's username/password filter ─────────
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        log.info("[SecurityConfig] Security filter chain initialised.");
        return http.build();
    }

    // ─────────────────────────────────────────────────────────────
    //  Authentication beans
    // ─────────────────────────────────────────────────────────────

    /**
     * DAO-backed authentication provider that delegates user loading to
     * {@link UserDetailsService} and password verification to {@link PasswordEncoder}.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        //provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * BCrypt password encoder with cost factor 12.
     * Cost factor 12 is a good balance between brute-force resistance and
     * login latency (~250 ms on modern hardware).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Exposes the {@link AuthenticationManager} so that {@code AuthService}
     * can delegate username/password authentication during login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
