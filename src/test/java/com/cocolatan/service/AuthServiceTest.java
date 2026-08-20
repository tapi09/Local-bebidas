package com.cocolatan.service;

import com.cocolatan.model.User;
import com.cocolatan.repository.ConfigRepository;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthService authService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository);

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setPasswordHash(AuthService.hashPassword("admin123"));
        adminUser.setRole("ADMIN");
        adminUser.setDisplayName("Administrador");
    }

    @Test
    void loginWithCorrectCredentialsReturnsUser() throws SQLException {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        User result = authService.login("admin", "admin123");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("admin");
        assertThat(result.isAdmin()).isTrue();
    }

    @Test
    void loginWithWrongPasswordReturnsNull() throws SQLException {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        User result = authService.login("admin", "wrongpass");

        assertThat(result).isNull();
    }

    @Test
    void loginUpgradesLegacySha256HashToBCrypt() throws SQLException {
        User legacyUser = new User();
        legacyUser.setId(1L);
        legacyUser.setUsername("admin");
        legacyUser.setPasswordHash(AuthService.legacySha256("admin123"));
        legacyUser.setRole("ADMIN");
        legacyUser.setDisplayName("Administrador");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(legacyUser));

        User result = authService.login("admin", "admin123");

        assertThat(result).isNotNull();
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updatePassword(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertThat(captor.getValue()).startsWith("$2");
        assertThat(AuthService.verifyPassword("admin123", captor.getValue())).isTrue();
    }

    @Test
    void loginWithMissingUsernameReturnsNull() throws SQLException {
        when(userRepository.findByUsername("noexiste")).thenReturn(Optional.empty());

        User result = authService.login("noexiste", "pass123");

        assertThat(result).isNull();
    }

    @Test
    void loginSetsCurrentUser() throws SQLException {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        authService.login("admin", "admin123");

        assertThat(authService.getCurrentUser()).isNotNull();
        assertThat(authService.getCurrentUser().getUsername()).isEqualTo("admin");
    }

    @Test
    void logoutClearsCurrentUser() throws SQLException {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        authService.login("admin", "admin123");

        authService.logout();

        assertThat(authService.getCurrentUser()).isNull();
    }

    @Test
    void isAdminReturnsTrueForAdmin() throws SQLException {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        authService.login("admin", "admin123");

        assertThat(authService.isAdmin()).isTrue();
        assertThat(authService.isCajero()).isFalse();
    }

    @Test
    void isCajeroReturnsTrueForCajero() throws SQLException {
        User cajero = new User();
        cajero.setUsername("cajero1");
        cajero.setPasswordHash(AuthService.hashPassword("pass123"));
        cajero.setRole("CAJERO");
        cajero.setDisplayName("Cajero");
        when(userRepository.findByUsername("cajero1")).thenReturn(Optional.of(cajero));

        authService.login("cajero1", "pass123");

        assertThat(authService.isCajero()).isTrue();
        assertThat(authService.isAdmin()).isFalse();
    }

    @Test
    void isLoggedInReturnsFalseWhenNotLoggedIn() {
        assertThat(authService.isLoggedIn()).isFalse();
        assertThat(authService.isAdmin()).isFalse();
        assertThat(authService.isCajero()).isFalse();
    }

    @Test
    void verifyPasswordAcceptsBCryptHashes() {
        String hash = AuthService.hashPassword("admin123");

        assertThat(AuthService.verifyPassword("admin123", hash)).isTrue();
        assertThat(AuthService.verifyPassword("wrongpass", hash)).isFalse();
        assertThat(hash).startsWith("$2");
    }

    @Test
    void verifyPasswordAcceptsLegacySha256Hashes() {
        String legacy = AuthService.legacySha256("admin123");

        assertThat(AuthService.verifyPassword("admin123", legacy)).isTrue();
        assertThat(AuthService.verifyPassword("otra", legacy)).isFalse();
    }

    @Test
    void verifyPasswordRejectsNullOrEmptyInput() {
        assertThat(AuthService.verifyPassword(null, AuthService.hashPassword("admin123"))).isFalse();
        assertThat(AuthService.verifyPassword("admin123", null)).isFalse();
        assertThat(AuthService.verifyPassword("admin123", "")).isFalse();
        assertThat(AuthService.verifyPassword("", AuthService.hashPassword("x"))).isFalse();
    }

    @Test
    void loginWithSQLExceptionThrowsRuntimeException() throws SQLException {
        when(userRepository.findByUsername(anyString())).thenThrow(new SQLException("DB error"));

        try {
            authService.login("admin", "admin123");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Error al autenticar");
        }
    }

    @Test
    void changePasswordUpdatesHashViaRepository() throws SQLException {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        authService.login("admin", "admin123");

        authService.changePassword(1L, "nuevapass");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updatePassword(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertThat(AuthService.verifyPassword("nuevapass", captor.getValue())).isTrue();
    }

    @Test
    void changePasswordClearsMustChangeFlagOnCurrentUser() throws SQLException {
        adminUser.setMustChangePassword(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        authService.login("admin", "admin123");

        authService.changePassword(1L, "nuevapass");

        assertThat(authService.getCurrentUser().isMustChangePassword()).isFalse();
    }

    // --- master key recovery ---

    @Test
    void verifyMasterKeyReturnsTrueForCorrectKey() throws SQLException {
        DatabaseManager db = DatabaseManager.createInMemory();
        try {
            ConfigRepository configRepo = new ConfigRepository(db);
            configRepo.set("master_reset_hash", AuthService.hashPassword("clave-secreta"));
            AuthService service = new AuthService(new UserRepository(db), configRepo);

            assertThat(service.verifyMasterKey("clave-secreta")).isTrue();
        } finally {
            db.close();
        }
    }

    @Test
    void verifyMasterKeyReturnsFalseForWrongKey() throws SQLException {
        DatabaseManager db = DatabaseManager.createInMemory();
        try {
            ConfigRepository configRepo = new ConfigRepository(db);
            configRepo.set("master_reset_hash", AuthService.hashPassword("clave-secreta"));
            AuthService service = new AuthService(new UserRepository(db), configRepo);

            assertThat(service.verifyMasterKey("otra-clave")).isFalse();
        } finally {
            db.close();
        }
    }

    @Test
    void verifyMasterKeyReturnsFalseWhenNotConfigured() throws SQLException {
        ConfigRepository configRepo = mock(ConfigRepository.class);
        when(configRepo.get("master_reset_hash")).thenReturn(Optional.empty());
        AuthService service = new AuthService(userRepository, configRepo);

        assertThat(service.verifyMasterKey("cualquiera")).isFalse();
    }

    @Test
    void setMasterKeyPersistsNewHash() throws SQLException {
        DatabaseManager db = DatabaseManager.createInMemory();
        try {
            ConfigRepository configRepo = new ConfigRepository(db);
            AuthService service = new AuthService(new UserRepository(db), configRepo);

            service.setMasterKey("clave-uno");
            service.setMasterKey("clave-dos");

            assertThat(service.verifyMasterKey("clave-dos")).isTrue();
            assertThat(service.verifyMasterKey("clave-uno")).isFalse();
        } finally {
            db.close();
        }
    }

    @Test
    void freshDatabaseIsNotMarkedAsUsingLegacyMasterKey() throws SQLException {
        DatabaseManager db = DatabaseManager.createInMemory();
        try {
            ConfigRepository configRepo = new ConfigRepository(db);
            AuthService service = new AuthService(new UserRepository(db), configRepo);

            assertThat(service.isRecoveryConfigured()).isFalse();
            assertThat(service.isUsingLegacyMasterKey()).isFalse();
        } finally {
            db.close();
        }
    }

    @Test
    void isUsingLegacyMasterKeyDetectsFactoryDefaultHash() throws SQLException {
        DatabaseManager db = DatabaseManager.createInMemory();
        try {
            ConfigRepository configRepo = new ConfigRepository(db);
            configRepo.set("master_reset_hash",
                    AuthService.hashPassword("cocolatan-master-2026"));
            AuthService service = new AuthService(new UserRepository(db), configRepo);

            assertThat(service.isUsingLegacyMasterKey()).isTrue();
        } finally {
            db.close();
        }
    }

    @Test
    void isUsingLegacyMasterKeyIsFalseAfterRotation() throws SQLException {
        DatabaseManager db = DatabaseManager.createInMemory();
        try {
            ConfigRepository configRepo = new ConfigRepository(db);
            AuthService service = new AuthService(new UserRepository(db), configRepo);
            service.setMasterKey("clave-rotada");

            assertThat(service.isUsingLegacyMasterKey()).isFalse();
        } finally {
            db.close();
        }
    }

    @Test
    void isUsingLegacyMasterKeyIsFalseWhenNotConfigured() throws SQLException {
        ConfigRepository configRepo = mock(ConfigRepository.class);
        when(configRepo.get("master_reset_hash")).thenReturn(Optional.empty());
        AuthService service = new AuthService(userRepository, configRepo);

        assertThat(service.isUsingLegacyMasterKey()).isFalse();
    }

    @Test
    void resetPasswordUpdatesHashAndClearsMustChangeFlag() throws SQLException {
        DatabaseManager db = DatabaseManager.createInMemory();
        try {
            UserRepository userRepo = new UserRepository(db);
            User target = new User();
            target.setUsername("olvidado");
            target.setPasswordHash(AuthService.hashPassword("vieja"));
            target.setRole("CAJERO");
            target.setDisplayName("Olvidado");
            target.setMustChangePassword(true);
            userRepo.save(target);

            ConfigRepository configRepo = new ConfigRepository(db);
            AuthService service = new AuthService(userRepo, configRepo);

            service.resetPassword(target.getId(), "nueva");

            User updated = userRepo.findByUsername("olvidado").orElseThrow();
            assertThat(AuthService.verifyPassword("nueva", updated.getPasswordHash())).isTrue();
            assertThat(updated.isMustChangePassword()).isFalse();
        } finally {
            db.close();
        }
    }

    @Test
    void resetPasswordRejectsUnknownUser() throws SQLException {
        DatabaseManager db = DatabaseManager.createInMemory();
        try {
            ConfigRepository configRepo = new ConfigRepository(db);
            AuthService service = new AuthService(new UserRepository(db), configRepo);

            assertThatThrownBy(() -> service.resetPassword(999L, "nueva"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("usuario");
        } finally {
            db.close();
        }
    }
}
