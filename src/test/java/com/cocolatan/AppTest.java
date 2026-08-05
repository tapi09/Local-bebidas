package com.cocolatan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppTest {

    @Test
    void appClassExists() {
        assertThat(CocolatanApp.class).isNotNull();
    }
}