package com.softwaredebebidas.view;

import com.softwaredebebidas.model.User;
import com.softwaredebebidas.presenter.LoginPresenter;
import com.softwaredebebidas.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginControllerTest {

    @Mock
    private LoginPresenter presenter;

    // --- validateCredentials ---

    @Test
    void validateCredentialsAcceptsFilledValues() {
        assertThat(LoginController.validateCredentials("admin", "secret")).isNull();
    }

    @Test
    void validateCredentialsRejectsBlankUsername() {
        assertThat(LoginController.validateCredentials("", "secret")).isEqualTo("Ingrese un nombre de usuario.");
        assertThat(LoginController.validateCredentials("   ", "secret")).isEqualTo("Ingrese un nombre de usuario.");
        assertThat(LoginController.validateCredentials(null, "secret")).isEqualTo("Ingrese un nombre de usuario.");
    }

    @Test
    void validateCredentialsRejectsBlankPassword() {
        assertThat(LoginController.validateCredentials("admin", "")).isEqualTo("Ingrese una contraseña.");
        assertThat(LoginController.validateCredentials("admin", null)).isEqualTo("Ingrese una contraseña.");
    }

    @Test
    void validateCredentialsPrefersUsernameErrorWhenBothBlank() {
        assertThat(LoginController.validateCredentials("", "")).isEqualTo("Ingrese un nombre de usuario.");
    }

    // --- validatePasswordChange ---

    @Test
    void validatePasswordChangeAcceptsFilledMatchingValues() {
        assertThat(LoginController.validatePasswordChange("old", "new", "new")).isNull();
    }

    @Test
    void validatePasswordChangeRejectsBlankCurrentPassword() {
        assertThat(LoginController.validatePasswordChange("", "new", "new"))
                .isEqualTo("Ingrese su contraseña actual.");
        assertThat(LoginController.validatePasswordChange(null, "new", "new"))
                .isEqualTo("Ingrese su contraseña actual.");
    }

    @Test
    void validatePasswordChangeRejectsBlankNewPassword() {
        assertThat(LoginController.validatePasswordChange("old", "", "new"))
                .isEqualTo("Ingrese una contraseña nueva.");
    }

    @Test
    void validatePasswordChangeRejectsBlankConfirm() {
        assertThat(LoginController.validatePasswordChange("old", "new", ""))
                .isEqualTo("Confirme la contraseña nueva.");
    }

    @Test
    void validatePasswordChangeRejectsMismatch() {
        assertThat(LoginController.validatePasswordChange("old", "new", "other"))
                .isEqualTo("Las contraseñas no coinciden.");
    }

    @Test
    void validatePasswordChangeRejectsSameAsCurrent() {
        assertThat(LoginController.validatePasswordChange("same", "same", "same"))
                .isEqualTo("La contraseña nueva debe ser distinta a la actual.");
    }

    // --- evaluatePasswordChange ---

    @Test
    void evaluatePasswordChangeProceedsWhenNoChangeRequired() {
        User user = new User();
        user.setRole("ADMIN");

        LoginController.PasswordChangeResult result =
                LoginController.evaluatePasswordChange(presenter, user, null);

        assertThat(result.proceed()).isTrue();
        verify(presenter, never()).logout();
    }

    @Test
    void evaluatePasswordChangeWithValidInputProceeds() {
        User user = userRequiringChange();
        user.setPasswordHash(AuthService.hashPassword("oldpass"));
        when(presenter.currentPasswordMatches(user, "oldpass")).thenReturn(true);

        LoginController.PasswordChangeInput input =
                new LoginController.PasswordChangeInput("oldpass", "newpass", "newpass");
        LoginController.PasswordChangeResult result =
                LoginController.evaluatePasswordChange(presenter, user, input);

        assertThat(result.proceed()).isTrue();
        verify(presenter).changePassword(user.getId(), "newpass");
        verify(presenter, never()).logout();
    }

    @Test
    void evaluatePasswordChangeCancelledStaysOnLogin() {
        User user = userRequiringChange();

        LoginController.PasswordChangeResult result =
                LoginController.evaluatePasswordChange(presenter, user, null);

        assertThat(result.proceed()).isFalse();
        verify(presenter).logout();
        verify(presenter, never()).changePassword(any(), anyString());
    }

    @Test
    void evaluatePasswordChangeWithMismatchedConfirmStaysOnLogin() {
        User user = userRequiringChange();

        LoginController.PasswordChangeInput input =
                new LoginController.PasswordChangeInput("oldpass", "newpass", "different");
        LoginController.PasswordChangeResult result =
                LoginController.evaluatePasswordChange(presenter, user, input);

        assertThat(result.proceed()).isFalse();
        assertThat(result.errorMessage()).isNotNull();
        verify(presenter).logout();
        verify(presenter, never()).changePassword(any(), anyString());
    }

    @Test
    void evaluatePasswordChangeWithWrongCurrentPasswordStaysOnLogin() {
        User user = userRequiringChange();
        when(presenter.currentPasswordMatches(user, "wrong")).thenReturn(false);

        LoginController.PasswordChangeInput input =
                new LoginController.PasswordChangeInput("wrong", "newpass", "newpass");
        LoginController.PasswordChangeResult result =
                LoginController.evaluatePasswordChange(presenter, user, input);

        assertThat(result.proceed()).isFalse();
        assertThat(result.errorMessage()).isNotNull();
        verify(presenter).logout();
        verify(presenter, never()).changePassword(any(), anyString());
    }

    @Test
    void evaluatePasswordChangeWithChangeErrorStaysOnLogin() {
        User user = userRequiringChange();
        when(presenter.currentPasswordMatches(user, "oldpass")).thenReturn(true);
        doThrow(new RuntimeException("db down")).when(presenter).changePassword(user.getId(), "newpass");

        LoginController.PasswordChangeInput input =
                new LoginController.PasswordChangeInput("oldpass", "newpass", "newpass");
        LoginController.PasswordChangeResult result =
                LoginController.evaluatePasswordChange(presenter, user, input);

        assertThat(result.proceed()).isFalse();
        verify(presenter).logout();
    }

    // --- validateRecoveryInput ---

    @Test
    void validateRecoveryInputAcceptsFilledValues() {
        LoginController.RecoveryInput input =
                new LoginController.RecoveryInput(1L, "clave", "nueva", "nueva");

        assertThat(LoginController.validateRecoveryInput(input)).isNull();
    }

    @Test
    void validateRecoveryInputRejectsBlankKey() {
        LoginController.RecoveryInput input =
                new LoginController.RecoveryInput(1L, "", "nueva", "nueva");

        assertThat(LoginController.validateRecoveryInput(input))
                .isEqualTo("Ingrese la clave de recuperación.");
    }

    // --- evaluateRecovery ---

    @Test
    void evaluateRecoveryReturnsNotConfiguredMessageWhenDisabled() {
        LoginController.RecoveryInput input =
                new LoginController.RecoveryInput(1L, "clave", "nueva", "nueva");

        LoginController.RecoveryResult result = LoginController.evaluateRecovery(presenter, input);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage())
                .isEqualTo("La recuperación no está configurada. Contacte al proveedor.");
        verify(presenter, never()).resetPassword(any(), anyString());
    }

    @Test
    void evaluateRecoveryResetsPasswordOnValidInput() {
        when(presenter.isRecoveryConfigured()).thenReturn(true);
        when(presenter.verifyMasterKey("clave")).thenReturn(true);

        LoginController.RecoveryInput input =
                new LoginController.RecoveryInput(1L, "clave", "nueva", "nueva");
        LoginController.RecoveryResult result = LoginController.evaluateRecovery(presenter, input);

        assertThat(result.success()).isTrue();
        verify(presenter).resetPassword(1L, "nueva");
    }

    @Test
    void evaluateRecoveryRejectsWrongMasterKey() {
        when(presenter.isRecoveryConfigured()).thenReturn(true);
        when(presenter.verifyMasterKey("incorrecta")).thenReturn(false);

        LoginController.RecoveryInput input =
                new LoginController.RecoveryInput(1L, "incorrecta", "nueva", "nueva");
        LoginController.RecoveryResult result = LoginController.evaluateRecovery(presenter, input);

        assertThat(result.success()).isFalse();
        assertThat(result.wrongKey()).isTrue();
        assertThat(result.errorMessage()).isEqualTo("Clave de recuperación incorrecta.");
        verify(presenter, never()).resetPassword(any(), anyString());
    }

    @Test
    void evaluateRecoveryRejectsBlankNewPassword() {
        when(presenter.isRecoveryConfigured()).thenReturn(true);

        LoginController.RecoveryInput input =
                new LoginController.RecoveryInput(1L, "clave", "", "");
        LoginController.RecoveryResult result = LoginController.evaluateRecovery(presenter, input);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("Ingrese una contraseña nueva.");
        verify(presenter, never()).resetPassword(any(), anyString());
    }

    @Test
    void evaluateRecoveryRejectsMismatchedConfirm() {
        when(presenter.isRecoveryConfigured()).thenReturn(true);

        LoginController.RecoveryInput input =
                new LoginController.RecoveryInput(1L, "clave", "nueva", "otra");
        LoginController.RecoveryResult result = LoginController.evaluateRecovery(presenter, input);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("Las contraseñas no coinciden.");
        verify(presenter, never()).resetPassword(any(), anyString());
    }

    @Test
    void evaluateRecoveryHandlesResetFailure() {
        when(presenter.isRecoveryConfigured()).thenReturn(true);
        when(presenter.verifyMasterKey("clave")).thenReturn(true);
        doThrow(new RuntimeException("db down")).when(presenter).resetPassword(1L, "nueva");

        LoginController.RecoveryInput input =
                new LoginController.RecoveryInput(1L, "clave", "nueva", "nueva");
        LoginController.RecoveryResult result = LoginController.evaluateRecovery(presenter, input);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("No se pudo restablecer la contraseña.");
    }

    private User userRequiringChange() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setRole("ADMIN");
        user.setMustChangePassword(true);
        return user;
    }

    // --- versionFooterText ---

    @Test
    void versionFooterTextPrefixesGivenVersion() {
        assertThat(LoginController.versionFooterText("1.0.0-SNAPSHOT"))
                .isEqualTo("Versión 1.0.0-SNAPSHOT");
    }

    @Test
    void versionFooterTextHandlesUnknownVersion() {
        assertThat(LoginController.versionFooterText("unknown"))
                .isEqualTo("Versión unknown");
    }
}
