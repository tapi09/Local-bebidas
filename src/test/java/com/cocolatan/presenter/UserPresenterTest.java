package com.cocolatan.presenter;

import com.cocolatan.model.User;
import com.cocolatan.repository.UserRepository;
import com.cocolatan.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPresenterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    private UserPresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new UserPresenter(userRepository, authService);
    }

    // --- loadUsers ---

    @Test
    void loadUsersReturnsAllUsers() throws SQLException {
        List<User> users = List.of(createUser(1L, "admin", "ADMIN"), createUser(2L, "cajero1", "CAJERO"));
        when(userRepository.findAll()).thenReturn(users);

        List<User> result = presenter.loadUsers();

        assertThat(result).hasSize(2);
        verify(userRepository).findAll();
    }

    @Test
    void loadUsersReturnsEmptyList() throws SQLException {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        assertThat(presenter.loadUsers()).isEmpty();
    }

    @Test
    void loadUsersThrowsRuntimeExceptionOnSqlException() throws SQLException {
        when(userRepository.findAll()).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.loadUsers())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al cargar usuarios");
    }

    // --- createUser ---

    @Test
    void createUserPersistsUserWithHashedPasswordAndMustChangeFlag() throws SQLException {
        presenter.createUser("cajero1", "pass123", "pass123", "CAJERO", "Cajero Uno");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("cajero1");
        assertThat(AuthService.verifyPassword("pass123", saved.getPasswordHash())).isTrue();
        assertThat(saved.getRole()).isEqualTo("CAJERO");
        assertThat(saved.getDisplayName()).isEqualTo("Cajero Uno");
        assertThat(saved.isMustChangePassword()).isTrue();
    }

    @Test
    void createUserRejectsBlankUsername() {
        assertThatThrownBy(() -> presenter.createUser("", "pass", "pass", "CAJERO", "Nombre"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre de usuario");
    }

    @Test
    void createUserRejectsBlankDisplayName() {
        assertThatThrownBy(() -> presenter.createUser("cajero1", "pass", "pass", "CAJERO", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre a mostrar");
    }

    @Test
    void createUserRejectsEmptyPassword() {
        assertThatThrownBy(() -> presenter.createUser("cajero1", "", "", "CAJERO", "Nombre"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contraseña");
    }

    @Test
    void createUserRejectsPasswordMismatch() {
        assertThatThrownBy(() -> presenter.createUser("cajero1", "pass", "otra", "CAJERO", "Nombre"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no coinciden");
        verifyNoInteractions(userRepository);
    }

    @Test
    void createUserSurfacesDuplicateUsernameError() throws SQLException {
        doThrow(new SQLException("UNIQUE constraint failed: users.username"))
                .when(userRepository).save(any(User.class));

        assertThatThrownBy(() -> presenter.createUser("admin", "pass", "pass", "CAJERO", "Admin"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se pudo crear el usuario");
    }

    // --- updateUser ---

    @Test
    void updateUserDelegatesToRepository() throws SQLException {
        User user = createUser(1L, "cajero1", "CAJERO");
        user.setDisplayName("Nuevo Nombre");

        presenter.updateUser(user);

        verify(userRepository).update(user);
    }

    @Test
    void updateUserRejectsRemovingOwnAdminRoleWhenLastAdmin() throws SQLException {
        User current = createUser(1L, "admin", "ADMIN");
        when(authService.getCurrentUser()).thenReturn(current);
        when(userRepository.findAll()).thenReturn(List.of(current));

        User edit = createUser(1L, "admin", "CAJERO");

        assertThatThrownBy(() -> presenter.updateUser(edit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("último administrador");
        verify(userRepository, never()).update(any());
    }

    @Test
    void updateUserAllowsChangingOwnRoleWhenAnotherAdminExists() throws SQLException {
        User current = createUser(1L, "admin", "ADMIN");
        User other = createUser(2L, "admin2", "ADMIN");
        when(authService.getCurrentUser()).thenReturn(current);
        when(userRepository.findAll()).thenReturn(List.of(current, other));

        User edit = createUser(1L, "admin", "CAJERO");

        presenter.updateUser(edit);

        verify(userRepository).update(edit);
    }

    // --- changePassword (admin reset) ---

    @Test
    void changePasswordHashesAndSetsMustChangeFlag() throws SQLException {
        presenter.changePassword(5L, "newpass", "newpass");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updatePassword(org.mockito.ArgumentMatchers.eq(5L), captor.capture());
        assertThat(AuthService.verifyPassword("newpass", captor.getValue())).isTrue();
        verify(userRepository).setMustChangePassword(5L, true);
    }

    @Test
    void changePasswordRejectsMismatch() {
        assertThatThrownBy(() -> presenter.changePassword(5L, "a", "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no coinciden");
        verifyNoInteractions(userRepository);
    }

    @Test
    void changePasswordRejectsBlankPassword() {
        assertThatThrownBy(() -> presenter.changePassword(5L, "", ""))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRepository);
    }

    // --- deleteUser ---

    @Test
    void deleteUserDeletesSelectedUser() throws SQLException {
        User current = createUser(1L, "admin", "ADMIN");
        User target = createUser(2L, "cajero1", "CAJERO");
        when(authService.getCurrentUser()).thenReturn(current);
        when(userRepository.findAll()).thenReturn(List.of(current, target));

        presenter.deleteUser(2L);

        verify(userRepository).delete(2L);
    }

    @Test
    void deleteUserRejectsDeletingSelf() throws SQLException {
        User current = createUser(1L, "admin", "ADMIN");
        when(authService.getCurrentUser()).thenReturn(current);

        assertThatThrownBy(() -> presenter.deleteUser(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("propio usuario");
        verify(userRepository, never()).delete(anyLong());
    }

    @Test
    void deleteUserRejectsDeletingLastAdmin() throws SQLException {
        User current = createUser(1L, "admin", "ADMIN");
        User lastAdmin = createUser(2L, "admin2", "ADMIN");
        when(authService.getCurrentUser()).thenReturn(current);
        when(userRepository.findAll()).thenReturn(List.of(lastAdmin));

        assertThatThrownBy(() -> presenter.deleteUser(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos un administrador");
        verify(userRepository, never()).delete(anyLong());
    }

    @Test
    void deleteUserAllowsDeletingAdminWhenAnotherAdminExists() throws SQLException {
        User current = createUser(1L, "admin", "ADMIN");
        User otherAdmin = createUser(2L, "admin2", "ADMIN");
        when(authService.getCurrentUser()).thenReturn(current);
        when(userRepository.findAll()).thenReturn(List.of(current, otherAdmin));

        presenter.deleteUser(2L);

        verify(userRepository).delete(2L);
    }

    // --- master key ---

    @Test
    void isRecoveryConfiguredDelegatesToAuthService() {
        when(authService.isRecoveryConfigured()).thenReturn(true);
        assertThat(presenter.isRecoveryConfigured()).isTrue();

        when(authService.isRecoveryConfigured()).thenReturn(false);
        assertThat(presenter.isRecoveryConfigured()).isFalse();
    }

    @Test
    void changeMasterKeyPersistsNewKeyWhenCurrentKeyMatches() {
        when(authService.isRecoveryConfigured()).thenReturn(true);
        when(authService.verifyMasterKey("actual")).thenReturn(true);

        presenter.changeMasterKey("actual", "nueva", "nueva");

        verify(authService).setMasterKey("nueva");
    }

    @Test
    void changeMasterKeyRejectsWrongCurrentKey() {
        when(authService.isRecoveryConfigured()).thenReturn(true);
        when(authService.verifyMasterKey("incorrecta")).thenReturn(false);

        assertThatThrownBy(() -> presenter.changeMasterKey("incorrecta", "nueva", "nueva"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actual es incorrecta");
        verify(authService, never()).setMasterKey(any());
    }

    @Test
    void changeMasterKeyRejectsBlankNewKey() {
        assertThatThrownBy(() -> presenter.changeMasterKey("actual", "", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clave de recuperación es obligatoria");
        verify(authService, never()).setMasterKey(any());
    }

    @Test
    void changeMasterKeyRejectsMismatch() {
        assertThatThrownBy(() -> presenter.changeMasterKey("actual", "nueva", "otra"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no coinciden");
        verify(authService, never()).setMasterKey(any());
    }

    @Test
    void changeMasterKeyAllowsFirstTimeSetupWhenNotConfigured() {
        when(authService.isRecoveryConfigured()).thenReturn(false);

        presenter.changeMasterKey("", "primera", "primera");

        verify(authService).setMasterKey("primera");
    }

    private User createUser(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setDisplayName(username);
        return user;
    }
}
