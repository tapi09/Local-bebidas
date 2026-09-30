package com.softwaredebebidas.service;

import com.softwaredebebidas.model.User;
import com.softwaredebebidas.repository.ConfigRepository;
import com.softwaredebebidas.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

public class AuthService {

    private static final String MASTER_RESET_HASH_KEY = "master_reset_hash";

    private static AuthService instance;

    private final UserRepository userRepository;
    private final ConfigRepository configRepository;
    private User currentUser;

    public AuthService(UserRepository userRepository) {
        this(userRepository, null);
    }

    public AuthService(UserRepository userRepository, ConfigRepository configRepository) {
        this.userRepository = userRepository;
        this.configRepository = configRepository;
    }

    public static void initialize(UserRepository userRepository) {
        initialize(userRepository, null);
    }

    public static void initialize(UserRepository userRepository, ConfigRepository configRepository) {
        instance = new AuthService(userRepository, configRepository);
    }

    public static AuthService getInstance() {
        return instance;
    }

    public User login(String username, String password) {
        try {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                return null;
            }
            String storedHash = user.getPasswordHash();
            if (!verifyPassword(password, storedHash)) {
                return null;
            }
            // Upgrade legacy SHA-256 hashes to BCrypt on successful login.
            if (!storedHash.startsWith("$2")) {
                userRepository.updatePassword(user.getId(), hashPassword(password));
            }
            currentUser = user;
            return user;
        } catch (SQLException e) {
            throw new RuntimeException("Error al autenticar usuario", e);
        }
    }

    public void logout() {
        currentUser = null;
    }

    /**
     * Changes a user's password and clears the forced change flag (the user has
     * just set their own password). Keeps the in-memory current user consistent
     * when the target is the logged-in user.
     */
    public void changePassword(Long userId, String newPassword) {
        try {
            userRepository.updatePassword(userId, hashPassword(newPassword));
        } catch (SQLException e) {
            throw new RuntimeException("Error al cambiar la contraseña", e);
        }
        if (currentUser != null && currentUser.getId() != null && currentUser.getId().equals(userId)) {
            currentUser.setMustChangePassword(false);
        }
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.isAdmin();
    }

    public boolean isCajero() {
        return currentUser != null && currentUser.isCajero();
    }

    /**
     * Returns whether password recovery is configured (a master reset hash is
     * present in app_config).
     */
    public boolean isRecoveryConfigured() {
        if (configRepository == null) {
            return false;
        }
        try {
            return configRepository.get(MASTER_RESET_HASH_KEY).isPresent();
        } catch (SQLException e) {
            throw new RuntimeException("Error al consultar la configuración de recuperación", e);
        }
    }

    /**
     * Verifies a candidate provider master key against the stored hash. Returns
     * false when recovery is not configured, so callers must not reveal whether
     * a stored hash exists.
     */
    public boolean verifyMasterKey(String candidate) {
        if (candidate == null || configRepository == null) {
            return false;
        }
        try {
            return configRepository.get(MASTER_RESET_HASH_KEY)
                    .map(stored -> verifyPassword(candidate, stored))
                    .orElse(false);
        } catch (SQLException e) {
            throw new RuntimeException("Error al verificar la clave de recuperación", e);
        }
    }

    /**
     * Persists the hash (BCrypt) of a new provider master key. The plaintext key
     * is never stored.
     */
    public void setMasterKey(String newKey) {
        if (configRepository == null) {
            throw new RuntimeException("La recuperación de contraseña no está disponible.");
        }
        try {
            configRepository.set(MASTER_RESET_HASH_KEY, hashPassword(newKey));
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar la clave de recuperación", e);
        }
    }

    /**
     * Resets a user's password via the provider master key. The forced change
     * flag is cleared (the owner has just chosen the new password).
     */
    public void resetPassword(Long userId, String newPassword) {
        try {
            if (userRepository.findById(userId).isEmpty()) {
                throw new RuntimeException("El usuario no existe.");
            }
            userRepository.updatePassword(userId, hashPassword(newPassword));
        } catch (SQLException e) {
            throw new RuntimeException("Error al restablecer la contraseña", e);
        }
    }

    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    /**
     * Verifies a candidate password against a stored hash. Supports the current
     * BCrypt format and legacy unsalted SHA-256 hashes so existing databases keep
     * working. Returns false for null/empty/unknown formats.
     */
    public static boolean verifyPassword(String password, String storedHash) {
        if (password == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        if (storedHash.startsWith("$2")) {
            return BCrypt.checkpw(password, storedHash);
        }
        // Legacy unsalted SHA-256 (64 lowercase hex chars).
        return storedHash.equals(legacySha256(password));
    }

    /**
     * Legacy unsalted SHA-256 used by databases created before BCrypt migration.
     * Kept ONLY to verify and upgrade old hashes during login.
     */
    public static String legacySha256(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
