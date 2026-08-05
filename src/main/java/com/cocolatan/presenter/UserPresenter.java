package com.cocolatan.presenter;

import com.cocolatan.model.User;
import com.cocolatan.repository.UserRepository;
import com.cocolatan.service.AuthService;

import java.sql.SQLException;
import java.util.List;

/**
 * Presenter for the User management module.
 * Handles CRUD logic, validation and the admin-role protection rules,
 * delegating persistence to UserRepository.
 */
public class UserPresenter {

    private final UserRepository userRepository;
    private final AuthService authService;

    public UserPresenter(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    /**
     * Loads all users from the repository.
     */
    public List<User> loadUsers() {
        try {
            return userRepository.findAll();
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar usuarios", e);
        }
    }

    /**
     * Creates a new user. The new user starts with the forced password change
     * flag set, so they must pick their own password on first login.
     */
    public User createUser(String username, String password, String confirm, String role, String displayName) {
        String validationError = validateUserInput(username, displayName, password, confirm);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }
        User user = new User();
        user.setUsername(username.trim());
        user.setPasswordHash(AuthService.hashPassword(password));
        user.setRole(role);
        user.setDisplayName(displayName.trim());
        user.setMustChangePassword(true);
        try {
            userRepository.save(user);
            return user;
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo crear el usuario. Verifique que el nombre de usuario no esté en uso.", e);
        }
    }

    /**
     * Updates the mutable profile fields (display name and role) of an existing
     * user. Guards against demoting the last remaining ADMIN away from the
     * ADMIN role.
     */
    public void updateUser(User user) {
        User current = authService.getCurrentUser();
        if (current != null && current.getId() != null && current.getId().equals(user.getId()) && !user.isAdmin()) {
            long adminCount = loadUsers().stream().filter(User::isAdmin).count();
            if (adminCount <= 1) {
                throw new IllegalArgumentException("No puede quitar el rol de administrador al último administrador.");
            }
        }
        try {
            userRepository.update(user);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo actualizar el usuario.", e);
        }
    }

    /**
     * Admin password reset for an existing user. The new password is hashed and
     * the forced change flag is set so the user must set their own password on
     * the next login.
     */
    public void changePassword(Long userId, String newPassword, String confirm) {
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("La contraseña es obligatoria.");
        }
        if (confirm == null || confirm.isEmpty()) {
            throw new IllegalArgumentException("Confirme la contraseña.");
        }
        if (!newPassword.equals(confirm)) {
            throw new IllegalArgumentException("Las contraseñas no coinciden.");
        }
        try {
            userRepository.updatePassword(userId, AuthService.hashPassword(newPassword));
            userRepository.setMustChangePassword(userId, true);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cambiar la contraseña.", e);
        }
    }

    /**
     * Deletes a user, protecting the logged-in account and the last ADMIN.
     */
    public void deleteUser(Long userId) {
        User current = authService.getCurrentUser();
        if (current != null && current.getId() != null && current.getId().equals(userId)) {
            throw new IllegalArgumentException("No puede eliminar su propio usuario.");
        }
        User target = loadUsers().stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst()
                .orElse(null);
        if (target != null && target.isAdmin() && loadUsers().stream().filter(User::isAdmin).count() <= 1) {
            throw new IllegalArgumentException("Debe existir al menos un administrador.");
        }
        try {
            userRepository.delete(userId);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo eliminar el usuario.", e);
        }
    }

    /**
     * Returns whether password recovery is configured (provider master key set).
     */
    public boolean isRecoveryConfigured() {
        return authService.isRecoveryConfigured();
    }

    /**
     * Changes the provider master key. The current key must match when recovery
     * is already configured; first-time setup is allowed with an empty current
     * key. The new key is validated and persisted by AuthService.
     */
    public void changeMasterKey(String currentKey, String newKey, String confirm) {
        if (newKey == null || newKey.isEmpty()) {
            throw new IllegalArgumentException("La clave de recuperación es obligatoria.");
        }
        if (confirm == null || confirm.isEmpty()) {
            throw new IllegalArgumentException("Confirme la clave de recuperación.");
        }
        if (!newKey.equals(confirm)) {
            throw new IllegalArgumentException("Las claves no coinciden.");
        }
        if (authService.isRecoveryConfigured() && !authService.verifyMasterKey(currentKey)) {
            throw new IllegalArgumentException("La clave de recuperación actual es incorrecta.");
        }
        authService.setMasterKey(newKey);
    }

    private static String validateUserInput(String username, String displayName, String password, String confirm) {
        if (username == null || username.trim().isEmpty()) {
            return "El nombre de usuario es obligatorio.";
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            return "El nombre a mostrar es obligatorio.";
        }
        if (password == null || password.isEmpty()) {
            return "La contraseña es obligatoria.";
        }
        if (confirm == null || confirm.isEmpty()) {
            return "Confirme la contraseña.";
        }
        if (!password.equals(confirm)) {
            return "Las contraseñas no coinciden.";
        }
        return null;
    }
}
