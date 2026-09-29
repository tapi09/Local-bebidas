package com.softwaredebebidas.repository;

import com.softwaredebebidas.model.User;
import com.softwaredebebidas.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryTest {

    private DatabaseManager dbManager;
    private UserRepository repository;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
        repository = new UserRepository(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void findByUsernameReturnsUser() throws SQLException {
        User user = createUser("cajero1", "CAJERO");
        repository.save(user);

        Optional<User> found = repository.findByUsername("cajero1");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("cajero1");
        assertThat(found.get().getRole()).isEqualTo("CAJERO");
    }

    @Test
    void findByUsernameReturnsEmptyForMissingUser() throws SQLException {
        Optional<User> found = repository.findByUsername("noexiste");

        assertThat(found).isEmpty();
    }

    @Test
    void saveCreatesUserWithGeneratedId() throws SQLException {
        User user = createUser("admin", "ADMIN");

        repository.save(user);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getId()).isGreaterThan(0);
    }

    @Test
    void saveThrowsOnDuplicateUsername() throws SQLException {
        repository.save(createUser("admin", "ADMIN"));

        User dup = createUser("admin", "CAJERO");
        assertThatThrownBy(() -> repository.save(dup))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void findAllReturnsAllUsers() throws SQLException {
        repository.save(createUser("admin", "ADMIN"));
        repository.save(createUser("cajero1", "CAJERO"));
        repository.save(createUser("cajero2", "CAJERO"));

        List<User> users = repository.findAll();

        assertThat(users).hasSize(3);
    }

    @Test
    void updatePasswordModifiesHash() throws SQLException {
        User user = createUser("admin", "ADMIN");
        user.setPasswordHash("oldhash");
        repository.save(user);

        String newHash = AuthService.hashPassword("newpass");
        repository.updatePassword(user.getId(), newHash);

        Optional<User> found = repository.findByUsername("admin");
        assertThat(found).isPresent();
        assertThat(found.get().getPasswordHash()).isEqualTo(newHash);
    }

    @Test
    void savePersistsMustChangePasswordFlag() throws SQLException {
        User admin = createUser("admin", "ADMIN");
        admin.setMustChangePassword(true);
        repository.save(admin);

        assertThat(repository.findByUsername("admin").get().isMustChangePassword()).isTrue();

        User cajero = createUser("cajero1", "CAJERO");
        repository.save(cajero);

        assertThat(repository.findByUsername("cajero1").get().isMustChangePassword()).isFalse();
    }

    @Test
    void updateChangesDisplayNameAndRole() throws SQLException {
        User user = createUser("admin", "ADMIN");
        user.setDisplayName("Original");
        repository.save(user);

        user.setDisplayName("Renombrado");
        user.setRole("CAJERO");
        repository.update(user);

        Optional<User> found = repository.findByUsername("admin");
        assertThat(found).isPresent();
        assertThat(found.get().getDisplayName()).isEqualTo("Renombrado");
        assertThat(found.get().getRole()).isEqualTo("CAJERO");
    }

    @Test
    void updateDoesNotChangeUsernameOrPassword() throws SQLException {
        User user = createUser("admin", "ADMIN");
        user.setPasswordHash("oldhash");
        repository.save(user);

        user.setUsername("renombrado");
        user.setPasswordHash("newhash");
        repository.update(user);

        Optional<User> found = repository.findByUsername("admin");
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("admin");
        assertThat(found.get().getPasswordHash()).isEqualTo("oldhash");
    }

    @Test
    void updatePasswordResetsMustChangePasswordFlag() throws SQLException {
        User user = createUser("admin", "ADMIN");
        user.setMustChangePassword(true);
        repository.save(user);
        assertThat(repository.findByUsername("admin").get().isMustChangePassword()).isTrue();

        repository.updatePassword(user.getId(), AuthService.hashPassword("newpass"));

        assertThat(repository.findByUsername("admin").get().isMustChangePassword()).isFalse();
    }

    @Test
    void setMustChangePasswordUpdatesFlag() throws SQLException {
        User user = createUser("admin", "ADMIN");
        repository.save(user);

        repository.setMustChangePassword(user.getId(), true);
        assertThat(repository.findByUsername("admin").get().isMustChangePassword()).isTrue();

        repository.setMustChangePassword(user.getId(), false);
        assertThat(repository.findByUsername("admin").get().isMustChangePassword()).isFalse();
    }

    @Test
    void deleteRemovesUser() throws SQLException {
        User user = createUser("admin", "ADMIN");
        repository.save(user);

        repository.delete(user.getId());

        assertThat(repository.findByUsername("admin")).isEmpty();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void findByUsernameReturnsFullUserData() throws SQLException {
        User user = createUser("admin", "ADMIN");
        user.setDisplayName("Administrador");
        user.setPasswordHash("abc123");
        repository.save(user);

        Optional<User> found = repository.findByUsername("admin");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("admin");
        assertThat(found.get().getPasswordHash()).isEqualTo("abc123");
        assertThat(found.get().getRole()).isEqualTo("ADMIN");
        assertThat(found.get().getDisplayName()).isEqualTo("Administrador");
        assertThat(found.get().getId()).isEqualTo(user.getId());
    }

    private User createUser(String username, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(AuthService.hashPassword("pass123"));
        user.setRole(role);
        user.setDisplayName(username);
        return user;
    }
}
