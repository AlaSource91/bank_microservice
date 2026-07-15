package com.alaeldin.Auth_service.service;

import com.alaeldin.Auth_service.model.User;
import com.alaeldin.Auth_service.repository.UserRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

/**
 * Spring Security {@link UserDetailsService} implementation that loads a {@link User}
 * from the database and maps it to a Spring Security {@link UserDetails} object.
 *
 * <p>The query uses an {@code @EntityGraph} to eagerly fetch the full security graph
 * (roles → permissions → resource) in a single JOIN, preventing N+1 queries when
 * the authority list is built.</p>
 *
 * <p>Granted authorities follow two conventions:
 * <ul>
 *   <li><b>Role authorities</b> — prefixed with {@code "ROLE_"} (e.g. {@code "ROLE_ADMIN"}),
 *       required by Spring Security's {@code hasRole()} / {@code @PreAuthorize("hasRole(...)")}.</li>
 *   <li><b>Permission authorities</b> — in {@code "RESOURCE:ACTION"} format
 *       (e.g. {@code "ACCOUNT:READ"}), used with {@code hasAuthority()}.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user by username and builds a Spring Security {@link UserDetails} object
     * with roles and fine-grained permission authorities.
     *
     * @param email the login handle supplied by the caller
     * @return a fully populated {@link UserDetails} object
     * @throws UsernameNotFoundException if no user with the given username exists
     */
    @Override
    @Transactional(readOnly = true)
    public @NonNull UserDetails loadUserByUsername(@NonNull String email) throws UsernameNotFoundException {

        log.info("[UserDetailsServiceImpl] ━━━━ Loading user by Email={} ━━━━", email);

        User user = userRepository.findWithRolesAndPermissionsByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + email));

        log.info("[UserDetailsServiceImpl] Found user ID={}, email={}", user.getId(), user.getEmail());
        log.info("[UserDetailsServiceImpl] Password hash from DB: {}", user.getPasswordHash());
        log.info("[UserDetailsServiceImpl] Account active: {}, locked: {}", user.isActive(), user.isAccountLocked());
        log.info("[UserDetailsServiceImpl] Roles: {}", user.getRoles().stream().map(r -> r.getName()).toList());

        // Role authorities: "ROLE_ADMIN", "ROLE_USER", etc.
        Stream<SimpleGrantedAuthority> roleAuthorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()));

        // Permission authorities: "ACCOUNT:READ", "TRANSACTION:EXECUTE", etc.
        Stream<SimpleGrantedAuthority> permissionAuthorities = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getResource().getName().name()
                        + ":" + permission.getAction().name())
                .distinct()
                .map(SimpleGrantedAuthority::new);

        List<SimpleGrantedAuthority> authorities = Stream
                .concat(roleAuthorities, permissionAuthorities)
                .toList();

        log.info("[UserDetailsServiceImpl] Loaded {} authorities", authorities.size());

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .accountExpired(false)
                .credentialsExpired(false)
                .disabled(!user.isActive())
                .accountLocked(user.isAccountLocked())
                .build();

        log.info("[UserDetailsServiceImpl] ━━━━ UserDetails built successfully ━━━━");
        return userDetails;
    }
}
