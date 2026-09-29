package com.softwaredebebidas.presenter;

import com.softwaredebebidas.model.User;
import com.softwaredebebidas.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginPresenterTest {

    @Mock
    private AuthService authService;

    private LoginPresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new LoginPresenter(authService);
    }

    @Test
    void loginReturnsUserForValidCredentials() {
        User user = new User();
        user.setUsername("admin");
        user.setRole("ADMIN");
        when(authService.login("admin", "admin123")).thenReturn(user);

        User result = presenter.login("admin", "admin123");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("admin");
    }

    @Test
    void loginReturnsNullForInvalidCredentials() {
        when(authService.login(anyString(), anyString())).thenReturn(null);

        User result = presenter.login("baduser", "badpass");

        assertThat(result).isNull();
    }

    @Test
    void loginDelegatesToAuthService() {
        User user = new User();
        user.setUsername("cajero1");
        when(authService.login("cajero1", "pass123")).thenReturn(user);

        presenter.login("cajero1", "pass123");

        org.mockito.Mockito.verify(authService).login("cajero1", "pass123");
    }
}
