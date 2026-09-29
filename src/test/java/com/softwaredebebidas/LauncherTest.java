package com.softwaredebebidas;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class LauncherTest {

    @Test
    void mainDelegatesToSoftwareDeBebidasApp() {
        try (MockedStatic<SoftwareDeBebidasApp> app = mockStatic(SoftwareDeBebidasApp.class)) {
            String[] args = {"--foo", "bar"};
            Launcher.main(args);
            app.verify(() -> SoftwareDeBebidasApp.main(args));
        }
    }

    @Test
    void launcherIsNotAJavaFxApplication() {
        assertThat(Launcher.class.getSuperclass()).isEqualTo(Object.class);
    }
}
