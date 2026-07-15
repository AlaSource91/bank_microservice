package com.alaeldin.Auth_service.service;

import com.alaeldin.Auth_service.constant.AuthEventType;
import com.alaeldin.Auth_service.dto.*;
import com.alaeldin.Auth_service.exception.RoleNotFoundException;
import com.alaeldin.Auth_service.exception.UserAlreadyExistsException;
import com.alaeldin.Auth_service.exception.UserNotFoundException;
import com.alaeldin.Auth_service.model.Permissions;
import com.alaeldin.Auth_service.model.Role;
import com.alaeldin.Auth_service.model.User;
import com.alaeldin.Auth_service.repository.PermissionRepository;
import com.alaeldin.Auth_service.repository.ResourceRepository;
import com.alaeldin.Auth_service.repository.RoleRepository;
import com.alaeldin.Auth_service.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Service containing administrative operations for the P2P platform.
 *
 * <p>All methods are guarded by {@link PreAuthorize} — callers must hold
 * either {@code ADMIN_PANEL:READ} (query operations) or
 * {@code ADMIN_PANEL:WRITE} (mutation operations).</p>
 *
 * <p>Every mutation that affects a user's active permission set also
 * invalidates the corresponding Redis cache entry so that the next
 * JWT validation picks up the latest state immediately.</p>
 */
@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class AdminService {

    private static final String REDIS_PERMISSIONS_PREFIX = "auth:permissions:";
    private static final String DEFAULT_ROLE = "AUDITOR";

    private final PermissionRepository permissionRepository;
    private final UserRepository       userRepository;
    private final RoleRepository       roleRepository;
    private final ResourceRepository   resourceRepository;
    private final EventPublishAuthService eventPublishAuthService;
    private final RedisTemplate<String, String> redisTemplate;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates a new user account with admin privileges (bypasses normal registration flow).
     * 
     * <p>This method is intended for admin-initiated user creation and differs from 
     * normal registration in several ways:</p>
     * <ul>
     *   <li>Assigns the AUDITOR role by default instead of USER</li>
     *   <li>Requires ADMIN_PANEL:WRITE permission to invoke</li>
     *   <li>Publishes USER_REGISTERED event for downstream processing</li>
     * </ul>
     *
     * @param addNewUserRequest validated request containing user details
     * @param httpServletRequest HTTP request for audit logging
     * @param file optional identity document file (e.g., national ID, passport)
     * @return {@link AuthResponse} with newly created user information and tokens
     * @throws UserAlreadyExistsException if email, phone, or nationalId already exists
     * @throws IOException if file upload fails
     * @throws RuntimeException if the default AUDITOR role is not found in database
     */
    @PreAuthorize("hasAuthority('ADMIN_PANEL:WRITE')")
    public AuthResponse addNewUser(AddNewUserRequest addNewUserRequest,
                                   HttpServletRequest httpServletRequest,
                                   MultipartFile file) throws IOException {
        log.info("[AdminService] addNewUser attempt: email={}", addNewUserRequest.getEmail());
        
        // Log file upload info
        if (file != null && !file.isEmpty()) {
            log.info("Identity file received: name={}, size={} bytes, contentType={}",
                    file.getOriginalFilename(), file.getSize(), file.getContentType());
        } else {
            log.warn("No identity file provided for new user registration");
        }

        // Validate uniqueness of email
        if (userRepository.existsByEmail(addNewUserRequest.getEmail())) {
            throw new UserAlreadyExistsException(
                    "Email '" + addNewUserRequest.getEmail() + "' is already registered");
        }
        
        // Validate uniqueness of phone
        userRepository.findByPhone(addNewUserRequest.getPhone()).ifPresent(existingUser -> {
            throw new UserAlreadyExistsException(
                    "Phone '" + addNewUserRequest.getPhone() + "' is already registered");
        });
        
        // Validate uniqueness of nationalId
        userRepository.findByNationalId(addNewUserRequest.getNationalId()).ifPresent(existingUser -> {
            throw new UserAlreadyExistsException(
                    "National ID '" + addNewUserRequest.getNationalId() + "' is already registered");
        });

        // Fetch the default role for admin-created users
        Role userRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new RuntimeException(
                        "Default role '" + DEFAULT_ROLE + "' not found in the database. " +
                        "Please ensure database migration has been executed."));
        
        // Save identity file if provided
        String savedFilePath = authService.saveFile(
                file, 
                addNewUserRequest.getNationalId(), 
                "nationalId");
        
        // Build user entity with proper defaults
        User user = User.builder()
                .firstName(addNewUserRequest.getFirstName())
                .middleName(addNewUserRequest.getMiddleName())
                .lastName(addNewUserRequest.getLastName())
                .email(addNewUserRequest.getEmail())
                .phone(addNewUserRequest.getPhone())
                .nationalId(addNewUserRequest.getNationalId())
                .identityFilePath(savedFilePath)
                .passwordHash(passwordEncoder.encode(addNewUserRequest.getPassword()))
                .isActive(true)
                .failedLoginAttempts(0)
                .roles(java.util.Set.of(userRole))
                .build();

        User savedUser = userRepository.save(user);
        
        log.info("[AdminService] User created successfully: userId={}, email={}, role={}, identityFilePath={}",
                savedUser.getId(), savedUser.getEmail(), DEFAULT_ROLE, savedUser.getIdentityFilePath());
        
        // Publish event for downstream services
        eventPublishAuthService.saveAuthEventOutBox(savedUser, AuthEventType.USER_REGISTERED);

        return authService.buildTokensAndResponse(savedUser);
    }

    /**
     * Updates an existing user's information.
     *
     * <p>Only non-null fields in the request will be updated. This allows for partial updates.
     * If a password is provided, it will be properly hashed before storage.
     * If an identity file is provided, it will be validated and saved.</p>
     *
     * @param updateRequest     validated update payload with userId and optional fields to update
     * @param httpServletRequest HTTP request for audit logging
     * @param file              optional new identity document file
     * @return {@link AuthResponse} with updated user information and new tokens
     * @throws UserNotFoundException if no user exists with the given ID
     * @throws UserAlreadyExistsException if email/phone/nationalId already exists for another user
     * @throws IOException if file upload fails
     */
    @PreAuthorize("hasAuthority('ADMIN_PANEL:WRITE')")
    public AuthResponse updateUser(UpdateUserRequest updateRequest, 
                                    HttpServletRequest httpServletRequest,
                                    MultipartFile file) throws IOException {
        log.info("[AdminService] updateUser userId={}", updateRequest.getUserId());
        
        // Fetch existing user
        User user = userRepository.findById(updateRequest.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + updateRequest.getUserId()));
        
        boolean hasChanges = false;
        
        // Update firstName if provided
        if (updateRequest.getFirstName() != null && !updateRequest.getFirstName().equals(user.getFirstName())) {
            log.debug("Updating firstName: {} -> {}", user.getFirstName(), updateRequest.getFirstName());
            user.setFirstName(updateRequest.getFirstName());
            hasChanges = true;
        }
        
        // Update middleName if provided
        if (updateRequest.getMiddleName() != null && !updateRequest.getMiddleName().equals(user.getMiddleName())) {
            log.debug("Updating middleName: {} -> {}", user.getMiddleName(), updateRequest.getMiddleName());
            user.setMiddleName(updateRequest.getMiddleName());
            hasChanges = true;
        }
        
        // Update lastName if provided
        if (updateRequest.getLastName() != null && !updateRequest.getLastName().equals(user.getLastName())) {
            log.debug("Updating lastName: {} -> {}", user.getLastName(), updateRequest.getLastName());
            user.setLastName(updateRequest.getLastName());
            hasChanges = true;
        }
        
        // Update email if provided and validate uniqueness
        if (updateRequest.getEmail() != null && !updateRequest.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(updateRequest.getEmail())) {
                throw new UserAlreadyExistsException("Email '" + updateRequest.getEmail() + "' is already in use by another user");
            }
            log.debug("Updating email: {} -> {}", user.getEmail(), updateRequest.getEmail());
            user.setEmail(updateRequest.getEmail());
            hasChanges = true;
        }
        
        // Update phone if provided and validate uniqueness
        if (updateRequest.getPhone() != null && !updateRequest.getPhone().equals(user.getPhone())) {
            // Check if phone exists for a different user
            userRepository.findByPhone(updateRequest.getPhone()).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(user.getId())) {
                    throw new UserAlreadyExistsException("Phone '" + updateRequest.getPhone() + "' is already in use by another user");
                }
            });
            log.debug("Updating phone: {} -> {}", user.getPhone(), updateRequest.getPhone());
            user.setPhone(updateRequest.getPhone());
            hasChanges = true;
        }
        
        // Update nationalId if provided and validate uniqueness
        if (updateRequest.getNationalId() != null && !updateRequest.getNationalId().equals(user.getNationalId())) {
            userRepository.findByNationalId(updateRequest.getNationalId()).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(user.getId())) {
                    throw new UserAlreadyExistsException("National ID '" + updateRequest.getNationalId() + "' is already in use by another user");
                }
            });
            log.debug("Updating nationalId: {} -> {}", user.getNationalId(), updateRequest.getNationalId());
            user.setNationalId(updateRequest.getNationalId());
            hasChanges = true;
        }
        
        // Update password if provided - must be hashed
        if (updateRequest.getPassword() != null && !updateRequest.getPassword().isBlank()) {
            log.debug("Updating password for userId={}", user.getId());
            user.setPasswordHash(passwordEncoder.encode(updateRequest.getPassword()));
            hasChanges = true;
        }
        
        // Update identity file if provided
        if (file != null && !file.isEmpty()) {
            log.info("New identity file provided: name={}, size={} bytes", file.getOriginalFilename(), file.getSize());
            String savedFilePath = authService.saveFile(file, user.getNationalId(), "nationalId");
            user.setIdentityFilePath(savedFilePath);
            log.info("Identity file updated: path={}", savedFilePath);
            hasChanges = true;
        }
        
        // Save only if there were changes
        if (hasChanges) {
            User updatedUser = userRepository.save(user);
            invalidatePermissionsCache(updatedUser.getId());
            eventPublishAuthService.saveAuthEventOutBox(updatedUser, AuthEventType.USER_UPDATED);
            log.info("[AdminService] User updated successfully: userId={}", updatedUser.getId());
            return authService.buildTokensAndResponse(updatedUser);
        } else {
            log.info("[AdminService] No changes detected for userId={}", user.getId());
            return authService.buildTokensAndResponse(user);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  User queries
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ADMIN_PANEL:READ')")
    public UserProfileResponse getUserById(Long userId) {
        log.info("[AdminService] getUserById userId={}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        return UserProfileResponse.fromUser(user);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ADMIN_PANEL:READ')")
    public Page<UserProfileResponse> getAllUsers(Pageable pageable) {
        log.info("[AdminService] getAllUsers called");
        return userRepository.findAll(pageable)
                .map(UserProfileResponse::fromUser);
    }

    // ─────────────────────────────────────────────────────────────
    //  User status mutations
    // ─────────────────────────────────────────────────────────────

    /**
     * Activates a user account, allowing them to log in.
     *
     * @param userId the target user's primary key
     * @throws UserNotFoundException if no user exists with the given ID
     */
    @PreAuthorize("hasAuthority('ADMIN_PANEL:WRITE')")
    public void activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        user.setActive(true);
        userRepository.save(user);
        log.info("[AdminService] activateUser userId={}", userId);
    }

    /**
     * Deactivates a user account, preventing future logins.
     *
     * @param userId the target user's primary key
     * @throws UserNotFoundException if no user exists with the given ID
     */
    @PreAuthorize("hasAuthority('ADMIN_PANEL:WRITE')")
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        user.setActive(false);
        userRepository.save(user);
        log.info("[AdminService] deactivateUser userId={}", userId);
    }

    // ─────────────────────────────────────────────────────────────
    //  Role assignment
    // ─────────────────────────────────────────────────────────────

    /**
     * Assigns a role to a user and invalidates their permission cache.
     *
     * @param request   validated payload containing {@code userId} and {@code roleName}
     * @param auditedBy username of the admin performing the action (for audit log)
     * @return updated {@link UserProfileResponse}
     */
    @PreAuthorize("hasAuthority('ADMIN_PANEL:WRITE')")
    public UserProfileResponse assignRole(AssignRoleRequest request, String auditedBy) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + request.getUserId()));
        Role role = roleRepository.findByName(request.getRoleName())
                .orElseThrow(() -> new RoleNotFoundException("Role not found with name: " + request.getRoleName()));

        user.getRoles().add(role);
        userRepository.save(user);
        invalidatePermissionsCache(user.getId());
        eventPublishAuthService.saveAuthEventOutBox(user, AuthEventType.ROLE_ASSIGNED);

        log.info("[AdminService] assignRole userId={} roleName={} by={}", request.getUserId(), request.getRoleName(), auditedBy);
        return UserProfileResponse.fromUser(user);
    }

    /**
     * Revokes a role from a user and invalidates their permission cache.
     *
     * @param request validated payload containing {@code userId} and {@code roleName}
     * @return updated {@link UserProfileResponse}
     */
    @PreAuthorize("hasAuthority('ADMIN_PANEL:WRITE')")
    public UserProfileResponse revokeRole(AssignRoleRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + request.getUserId()));
        Role role = roleRepository.findByName(request.getRoleName())
                .orElseThrow(() -> new RoleNotFoundException("Role not found with name: " + request.getRoleName()));

        user.getRoles().remove(role);
        userRepository.save(user);
        invalidatePermissionsCache(user.getId());
        eventPublishAuthService.saveAuthEventOutBox(user, AuthEventType.ROLE_REVOKED);

        log.info("[AdminService] revokeRole userId={} roleName={}", request.getUserId(), request.getRoleName());
        return UserProfileResponse.fromUser(user);
    }

    // ─────────────────────────────────────────────────────────────
    //  Role / Resource / Permission queries
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ADMIN_PANEL:READ')")
    public Page<RoleResponse> getAllRoles(Pageable pageable) {
        log.info("[AdminService] getAllRoles called");
        return roleRepository.findAll(pageable)
                .map(role -> RoleResponse.builder()
                        .id(role.getId())
                        .name(role.getName())
                        .description(role.getDescription())
                        .permissions(role.getPermissions().stream()
                                .map(Permissions::toPermissionKey)
                                .collect(Collectors.toSet()))
                        .build());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ADMIN_PANEL:READ')")
    public Page<ResourceResponse> getAllResources(Pageable pageable) {
        log.info("[AdminService] getAllResources called");
        return resourceRepository.findAll(pageable)
                .map(ResourceResponse::fromResource);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ADMIN_PANEL:READ')")
    public Page<PermissionResponse> getAllPermissions(Pageable pageable) {
        log.info("[AdminService] getAllPermissions called");
        return permissionRepository.findAll(pageable)
                .map(PermissionResponse::fromPermission);
    }

    // ─────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Removes the cached permission set for the given user from Redis,
     * forcing a fresh load on the next authenticated request.
     *
     * @param userId the ID of the user whose cache entry should be evicted
     */
    private void invalidatePermissionsCache(Long userId) {
        redisTemplate.delete(REDIS_PERMISSIONS_PREFIX + userId);
    }



}
