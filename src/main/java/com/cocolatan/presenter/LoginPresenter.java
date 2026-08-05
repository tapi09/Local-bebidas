package com.cocolatan.presenter;

import com.cocolatan.model.User;
import com.cocolatan.service.AuthService;

public class LoginPresenter {

    private final AuthService authService;

    public LoginPresenter(AuthService authService) {
        this.authService = authService;
    }

    public User login(String username, String password) {
        return authService.login(username, password);
    }

    public void logout() {
        authService.logout();
    }

    /**
     * Verifies that the given password matches the user's current password hash.
     */
    public boolean currentPasswordMatches(User user, String password) {
        if (user == null || password == null) {
            return false;
        }
        return AuthService.hashPassword(password).equals(user.getPasswordHash());
    }

    public void changePassword(Long userId, String newPassword) {
        authService.changePassword(userId, newPassword);
    }

    /**
     * Returns whether password recovery is configured (provider master key set).
     */
    public boolean isRecoveryConfigured() {
        return authService.isRecoveryConfigured();
    }

    /**
     * Verifies a candidate provider master key against the stored hash.
     */
    public boolean verifyMasterKey(String candidate) {
        return authService.verifyMasterKey(candidate);
    }

    /**
     * Resets a user's password via the provider master key flow.
     */
    public void resetPassword(Long userId, String newPassword) {
        authService.resetPassword(userId, newPassword);
    }
}
