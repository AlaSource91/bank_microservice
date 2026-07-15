package com.alaeldin.Auth_service.service;

import com.alaeldin.Auth_service.component.CurrentUserUtil;
import com.alaeldin.Auth_service.constant.AuthEventType;
import com.alaeldin.Auth_service.dto.AuthResponse;
import com.alaeldin.Auth_service.dto.LoginRequest;
import com.alaeldin.Auth_service.dto.RefreshTokenRequest;
import com.alaeldin.Auth_service.dto.RegisterRequest;
import com.alaeldin.Auth_service.dto.UserProfileResponse;
import com.alaeldin.Auth_service.exception.InvalidCredentialsException;
import com.alaeldin.Auth_service.exception.RefreshTokenNotFoundException;
import com.alaeldin.Auth_service.exception.UserAlreadyExistsException;
import com.alaeldin.Auth_service.exception.UserNotFoundException;
import com.alaeldin.Auth_service.mapper.UserMapper;
import com.alaeldin.Auth_service.model.RefreshToken;
import com.alaeldin.Auth_service.model.Role;
import com.alaeldin.Auth_service.model.User;
import com.alaeldin.Auth_service.repository.RefreshTokenRepository;
import com.alaeldin.Auth_service.repository.RoleRepository;
import com.alaeldin.Auth_service.repository.UserRepository;
import com.alaeldin.Auth_service.util.FileUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core authentication service responsible for user registration, login,
 * token refresh, and logout.
 *
 * <p>All write operations are wrapped in a single Spring-managed transaction.
 * Auth domain events are published via the outbox pattern through
 * {@link EventPublishAuthService} to guarantee at-least-once delivery to Kafka.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AuthService {

    // ─────────────────────────────────────────────────────────────
    //  Constants
    // ─────────────────────────────────────────────────────────────

    private static final int    MAX_FAILED_ATTEMPTS   = 5;
    private static final int    LOCK_DURATION_MINUTES = 15;
    private static final String DEFAULT_ROLE          = "USER";
    private static final Long  MAX_FILE_SIZE_BYTES =  10 * 1024 * 1024L;
    private final UserMapper userMapper;

    // ─────────────────────────────────────────────────────────────
    //  Dependencies  (all final → injected via @RequiredArgsConstructor)
    // ─────────────────────────────────────────────────────────────

    private final UserRepository          userRepository;
    private final RoleRepository          roleRepository;
    private final PasswordEncoder         passwordEncoder;
    private final RefreshTokenRepository  refreshTokenRepository;
    private final JwtService              jwtService;
    private final AuthenticationManager   authenticationManager;
    private final EventPublishAuthService eventPublishAuthService;
    private final CurrentUserUtil         currentUserUtil;

    // ─────────────────────────────────────────────────────────────
    //  Configuration
    // ─────────────────────────────────────────────────────────────

    @Value("${app.jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    @Value("${upload.path}")
    private String uploadPath;

    @Value("${upload.file-prefix:identityId}")
    private String filePrefix;

    private static final Set<String> ALLOWED_EXTENSIONS
            = Set.of("jpg", "jpeg", "png", "pdf", "doc", "docx");

    // ─────────────────────────────────────────────────────────────
    //  Initialization
    // ─────────────────────────────────────────────────────────────

    /**
     * Initializes the upload directory on service startup.
     * Ensures the directory exists and logs the configuration.
     */
    @PostConstruct
    public void initUploadDirectory() {
        try {
            String currentDir = System.getProperty("user.dir");
            Path uploadDir = Paths.get(uploadPath);
            Path absoluteUploadDir = uploadDir.toAbsolutePath();

            log.info("╔════════════════════════════════════════════════════════════════");
            log.info("║ FILE UPLOAD CONFIGURATION");
            log.info("╠════════════════════════════════════════════════════════════════");
            log.info("║ Current working directory: {}", currentDir);
            log.info("║ Upload path (configured):  {}", uploadPath);
            log.info("║ Upload path (absolute):    {}", absoluteUploadDir);
            log.info("║ File prefix:               {}", filePrefix);
            log.info("║ Max file size:             {} MB", MAX_FILE_SIZE_BYTES / (1024 * 1024));
            log.info("║ Allowed extensions:        {}", ALLOWED_EXTENSIONS);
            log.info("╚════════════════════════════════════════════════════════════════");

            // Create directory if it doesn't exist
            if (!Files.exists(absoluteUploadDir)) {
                Files.createDirectories(absoluteUploadDir);
                log.info("✅ Created upload directory: {}", absoluteUploadDir);
            } else {
                log.info("✅ Upload directory already exists: {}", absoluteUploadDir);
            }

            // Verify directory is writable
            if (!Files.isWritable(absoluteUploadDir)) {
                log.error("❌ Upload directory is NOT writable: {}", absoluteUploadDir);
                throw new IOException("Upload directory is not writable: " + absoluteUploadDir);
            } else {
                log.info("✅ Upload directory is writable");
            }

            log.info("✅ File upload system initialized successfully");

        } catch (IOException e) {
            log.error("❌ FATAL: Failed to initialize upload directory", e);
            throw new RuntimeException("Cannot initialize file upload system", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Registers a new user, persists their account, and returns a token pair.
     *
     * <p>Validates uniqueness of both {@code username} and {@code email} before
     * persisting. A {@code USER_REGISTERED} event is dispatched to the outbox
     * within the same transaction so it is guaranteed to be published.</p>
     *
     * @param registerRequest validated registration payload
     * @param request         incoming HTTP request (used to capture the client IP)
     * @return {@link AuthResponse} containing the access and refresh tokens
     * @throws UserAlreadyExistsException if the username or email is already taken
     */
    @SuppressWarnings("unused") // HttpServletRequest reserved for future IP / audit-log enrichment
    public AuthResponse register(RegisterRequest registerRequest
            , HttpServletRequest request,MultipartFile file) throws IOException {
        log.info("Registration attempt: Email={}", registerRequest.getEmail());

        // Log file upload info
        if (file != null && !file.isEmpty()) {
            log.info("Identity file received: name={}, size={} bytes, contentType={}",
                    file.getOriginalFilename(), file.getSize(), file.getContentType());
        } else {
            log.warn("No identity file provided in registration request");
        }

        //check Email Is Already Exist
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new UserAlreadyExistsException(
                    "Email '" + registerRequest.getEmail() + "' is already in use");
        }

        //Extract Role
        Role userRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new RuntimeException("Default role USER not found in the database"));

        //Get File Name After Save
        String savedFilePath = saveFile(file , registerRequest.getNationalId(), "nationalId");
        log.info("Identity file saved: path={}", savedFilePath);

        User user = User.builder()
                .firstName(registerRequest.getFirstName())
                .middleName(registerRequest.getMiddleName())
                .lastName(registerRequest.getLastName())
                .email(registerRequest.getEmail())
                .phone(registerRequest.getPhone())
                .nationalId(registerRequest.getNationalId())
                .identityFilePath(savedFilePath)
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .isActive(true)
                .failedLoginAttempts(0)
                .roles(Set.of(userRole))
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: userId={}, identityFilePath={}",
                savedUser.getId(), savedUser.getIdentityFilePath());

        eventPublishAuthService.saveAuthEventOutBox(savedUser, AuthEventType.USER_REGISTERED);

        return buildTokensAndResponse(savedUser);
    }

    /**
     *
     * @param file File
     * @param nationalId from  User
     * @param fileType FileType
     * @return fileName
     * @throws IOException ioException
     */
       public String  saveFile (MultipartFile file,String nationalId, String fileType) throws IOException {
           if( file == null || file.isEmpty())
           {
               log.warn("File is null or empty, skipping upload");
               return "";
           }
           
           // Log current working directory for debugging
           String currentDir = System.getProperty("user.dir");
           log.info("Current working directory: {}", currentDir);
           log.info("Upload path configured as: {}", uploadPath);

           validateFile(file);

           // Build file name with proper separators
           String prefix = filePrefix.isEmpty() ? "" : filePrefix + "_";
           String extension = FileUtils.getFileExtension(file.getOriginalFilename());
           String fileName = prefix + nationalId + "_" + fileType + (extension.isEmpty() ? "" : "." + extension);

           // Use Paths.get with multiple arguments to properly handle path separators
           Path path = Paths.get(uploadPath, fileName);

           // Log the absolute path where file will be saved
           log.info("Attempting to save file to: {}", path.toAbsolutePath());

           // Create directories if they don't exist
           try {
               if (path.getParent() != null) {
                   Files.createDirectories(path.getParent());
                   log.info("Directory created/verified: {}", path.getParent().toAbsolutePath());
               }
           } catch (IOException e) {
               log.error("Failed to create directory: {}", path.getParent(), e);
               throw new IOException("Cannot create upload directory: " + path.getParent(), e);
           }

           // Write file to disk
           try {
               Files.write(path, file.getBytes());
               log.info("✅ File saved successfully: {} (Size: {} bytes)", path.toAbsolutePath(), file.getSize());

               // Verify file was actually written
               if (Files.exists(path)) {
                   long fileSize = Files.size(path);
                   log.info("✅ File verified on disk: {} (Size: {} bytes)", path.toAbsolutePath(), fileSize);
               } else {
                   log.error("❌ File was written but cannot be found at: {}", path.toAbsolutePath());
               }
           } catch (IOException e) {
               log.error("Failed to write file to: {}", path.toAbsolutePath(), e);
               throw new IOException("Cannot write file to disk: " + path.toAbsolutePath(), e);
           }

           return fileName;
       }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(String.format(
                    "File exceeds the 10 MB size limit: %s (Size: %.2f MB)",
                    file.getOriginalFilename(),
                    file.getSize() / (1024.0 * 1024.0)));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File must have a valid filename");
        }

        String ext = FileUtils.getFileExtension(originalFilename).toLowerCase();
        if (ext.isEmpty()) {
            throw new IllegalArgumentException("File must have an extension. Allowed: " + ALLOWED_EXTENSIONS);
        }

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException(String.format(
                    "Invalid file extension: '%s'. Allowed extensions: %s",
                    ext,
                    ALLOWED_EXTENSIONS));
        }

        log.debug("File validation passed: {} ({} bytes, extension: {})",
                originalFilename, file.getSize(), ext);
    }


    /**
     * Authenticates a user by Email and password, resets any failed-attempt counter,
     * and returns a fresh token pair.
     *
     * <p>If the account is currently locked an {@link InvalidCredentialsException} is thrown
     * immediately, before any password comparison, to avoid leaking timing information.
     * On each failed attempt the counter is incremented; when it reaches
     * {@value #MAX_FAILED_ATTEMPTS} the account is locked for {@value #LOCK_DURATION_MINUTES}
     * minutes.</p>
     *
     * @param loginRequest validated login payload
     * @param request      incoming HTTP request (used to capture the client IP)
     * @return {@link AuthResponse} containing a fresh access and refresh token pair
     * @throws UserNotFoundException      if no user exists with the given Email
     * @throws InvalidCredentialsException if the account is locked or the password is wrong
     */
    @SuppressWarnings("unused") // HttpServletRequest reserved for future IP / audit-log enrichment
    public AuthResponse login(LoginRequest loginRequest, HttpServletRequest request) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("LOGIN ATTEMPT for email: {}", loginRequest.getEmail());
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found: " + loginRequest.getEmail()));

        log.info("[AuthService] User found: ID={}, Email={}", user.getId(), user.getEmail());
        log.info("[AuthService] User password hash: {}", user.getPasswordHash());
        log.info("[AuthService] Account locked: {}, Active: {}", user.isAccountLocked(), user.isActive());

        if (user.isAccountLocked()) {
            log.warn("Login blocked — account locked: Email={}", user.getEmail());
            throw new InvalidCredentialsException("Account is temporarily locked. Please try again later.");
        }

        log.info("[AuthService] Calling authenticationManager.authenticate()...");
        log.info("[AuthService] Raw password length: {}", loginRequest.getPassword().length());
        log.info("[AuthService] Password starts with: {}", loginRequest.getPassword().substring(0, Math.min(3, loginRequest.getPassword().length())));

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                           loginRequest.getPassword()));

            log.info("✓✓✓ Authentication SUCCESSFUL for email: {}", user.getEmail());

        } catch (BadCredentialsException ex) {
            log.error("✗✗✗ Authentication FAILED for email: {}", user.getEmail());
            log.error("✗✗✗ Exception: {}", ex.getMessage());
            handleFailedLogin(user);
            throw new InvalidCredentialsException("Invalid Email or password.");
        }

        // Successful login — reset the failed-attempt counter via a targeted bulk UPDATE
        userRepository.resetFailedLoginAttempts(user.getId());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        log.info("Login successful: userId={}", user.getId());
        eventPublishAuthService.saveAuthEventOutBox(user, AuthEventType.USER_LOGIN);

        return buildTokensAndResponse(user);
    }

    /**
     * Rotates the refresh token: validates the supplied token, issues a new token pair,
     * and invalidates the old refresh token.
     *
     * @param refreshTokenRequest payload containing the current refresh token
     * @return {@link AuthResponse} containing a new access and refresh token pair
     * @throws RefreshTokenNotFoundException if the token is not found, expired, or revoked
     */
    public AuthResponse refreshToken(RefreshTokenRequest refreshTokenRequest) {
        String rawToken = refreshTokenRequest.getRefreshToken();

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(rawToken)
                .orElseThrow(() -> new RefreshTokenNotFoundException("Refresh token not found or already used."));

        if (storedToken.isInvalid()) {
            log.warn("Refresh token is invalid (expired or revoked): userId={}", storedToken.getUser().getId());
            throw new RefreshTokenNotFoundException("Refresh token has expired or been revoked. Please log in again.");
        }

        User user = storedToken.getUser();

        // Invalidate the old token (rotation)
        refreshTokenRepository.delete(storedToken);

        log.info("Refresh token rotated: userId={}", user.getId());
        eventPublishAuthService.saveAuthEventOutBox(user, AuthEventType.TOKEN_REFRESHED);

        return buildTokensAndResponse(user);
    }

    /**
     * Returns the profile of the currently authenticated user.
     *
     * @param email the username extracted from the validated JWT (never {@code null}
     *                 when called from a secured endpoint)
     * @return a {@link UserProfileResponse} for the authenticated user
     * @throws UserNotFoundException if no account exists for the given username
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(String email) {
        log.info("Profile lookup: Email={}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
        return UserProfileResponse.fromUser(user);
    }

    /**
     * Logs the user out by blacklisting the access token and revoking all refresh tokens
     * belonging to the user identified by the access token.
     *
     * @param accessToken raw JWT access token (without {@code "Bearer "} prefix)
     */
    public void logout(String accessToken) {
        String email = jwtService.extractUsername(accessToken);
        log.info("Logout: username={}", email);

        // Blacklist the access token for its remaining TTL
        jwtService.blacklistToken(accessToken);

        // Revoke all refresh tokens for this user
        userRepository.findByEmail(email).ifPresent(user -> {
            refreshTokenRepository.deleteByUser_Id(user.getId());
            eventPublishAuthService.saveAuthEventOutBox(user, AuthEventType.USER_LOGOUT);
            log.info("All refresh tokens revoked: userId={}", user.getId());
        });
    }

    // ─────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Increments the failed-login counter via a targeted bulk UPDATE and locks the
     * account if the threshold is reached.
     *
     * <p>Uses {@link com.alaeldin.Auth_service.repository.UserRepository#updateLockStatus}
     * instead of a load+modify+save cycle to avoid an extra SELECT and dirty-check under
     * high authentication load.</p>
     */
    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;

        LocalDateTime lockedUntil = attempts >= MAX_FAILED_ATTEMPTS
                ? LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES)
                : null; // no lock yet; clear any expired lock window

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            log.warn("Account locked after {} failed attempts: username={}", attempts, user.getEmail());
        }

        userRepository.updateLockStatus(user.getId(), attempts, lockedUntil);
    }

    /**
     * Issues a new access + refresh token pair, persists the refresh token,
     * and builds the {@link AuthResponse} returned to the client.
     *
     * @param user the authenticated user entity (roles must be initialised)
     * @return a fully-populated {@link AuthResponse}
     */
    public AuthResponse buildTokensAndResponse(User user) {
        String accessToken       = jwtService.generateAccessToken(user);
        String refreshTokenValue = jwtService.generateRefreshTokenValue();

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(refreshTokenValue)
                .user(user)
                .expiryDate(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        Set<String> roleNames   = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        Set<String> permissions = jwtService.buildPermissionList(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpirationMs / 1000)
                .userId(user.getId())
                .firstName(user.getFirstName())
                .middleName(user.getMiddleName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .nationalId(user.getNationalId())
                .roles(roleNames)
                .permissions(permissions)
                .build();
    }

    private Path readUploadDirectory()
    {
        try
        {
            String currentDir =  System.getProperty("user.dir");
            Path uploadDir = Paths.get( uploadPath);
            Path absoluteUploadDir = uploadDir.toAbsolutePath();

            log.info("Current dir={}", currentDir);
            log.info("Current dir={}", absoluteUploadDir.toAbsolutePath());

           return absoluteUploadDir;
        }
        catch (Exception e){

            throw new RuntimeException(e);
        }
    }

    public byte[] readFile(String fileName) throws IOException {

        // Validate input
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name is null or empty");
        }

        Long userId = userRepository.findIdByIdentityFile(fileName);
        Long currentUserId = currentUserUtil.getUserId();

        // Authorization check
        if (!Objects.equals(userId, currentUserId)
                && !currentUserUtil.isAdmin()) {

            throw new SecurityException("Access denied");
        }

        Path path = readUploadDirectory()
                .resolve(fileName)
                .normalize();

        log.info("File path={}", path.toAbsolutePath());

        // File exists check
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found");
        }

        return Files.readAllBytes(path);
    }

    public boolean deleteFileAndAccount() throws IOException
   {
       Long  userId = currentUserUtil.getUserId();
       User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
       String fileName = user.getIdentityFilePath();
       if (fileName.isBlank())
       {
           throw new IOException("File name is null or empty");
       }

       Path path = Paths.get(uploadPath,fileName);
      boolean deleted = Files.deleteIfExists(path);
      if (!deleted)
       {
           log.warn("File {} could not be deleted", fileName);
           throw new IOException("Unable to delete file: " + fileName);
       }
      else {
          deleteMyAccount(userId);
          log.info("Deleted file: " + fileName);

          return deleted;
      }
   }

   public void
   deleteMyAccount(Long id)
   {
       User user =userRepository.findById(id)
               .orElseThrow(()-> new UserNotFoundException("User not found with id:" + id));
       userRepository.delete(user);
   }

   public UserProfileResponse getCurrentUserById(Long id)
   {
         User user = userRepository.findById(id)
               .orElseThrow(()-> new UserNotFoundException("User not found with id:" + id));

         return userMapper.toUserProfileResponse(user);
   }
}
