package com.softwaredebebidas.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerTest {

    // --- roleDisplay ---

    @Test
    void roleDisplayMapsAdmin() {
        assertThat(UserController.roleDisplay("ADMIN")).isEqualTo("Administrador");
    }

    @Test
    void roleDisplayMapsCajero() {
        assertThat(UserController.roleDisplay("CAJERO")).isEqualTo("Cajero");
    }

    @Test
    void roleDisplayHandlesNull() {
        assertThat(UserController.roleDisplay(null)).isEmpty();
    }

    @Test
    void roleDisplayHandlesUnknownRole() {
        assertThat(UserController.roleDisplay("GERENTE")).isEmpty();
    }
}
