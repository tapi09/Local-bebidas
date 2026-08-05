package com.cocolatan;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class LauncherTest {

    @Test
    void mainDelegatesToCocolatanApp() {
        try (MockedStatic<CocolatanApp> app = mockStatic(CocolatanApp.class)) {
            String[] args = {"--foo", "bar"};
            Launcher.main(args);
            app.verify(() -> CocolatanApp.main(args));
        }
    }

    @Test
    void launcherIsNotAJavaFxApplication() {
        assertThat(Launcher.class.getSuperclass()).isEqualTo(Object.class);
    }
}
