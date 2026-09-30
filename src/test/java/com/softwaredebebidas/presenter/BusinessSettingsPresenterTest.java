package com.softwaredebebidas.presenter;

import com.softwaredebebidas.repository.ConfigRepository;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.util.LogoUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessSettingsPresenterTest {

    @TempDir
    Path dataDir;

    private DatabaseManager db;
    private ConfigRepository config;
    private BusinessSettingsPresenter presenter;

    @BeforeEach
    void setUp() {
        db = DatabaseManager.createInMemory();
        config = new ConfigRepository(db);
        presenter = new BusinessSettingsPresenter(config, dataDir);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void loadsTheGenericDefaultNameAndNoLogoOnFreshInstall() {
        assertThat(presenter.getBusinessName()).isEqualTo("Mi negocio");
        assertThat(presenter.getLogoPath()).isNull();
    }

    @Test
    void savePersistsTheTrimmedName() throws SQLException {
        presenter.save("  Distribuidora Norte ", null, false);

        assertThat(config.getBusinessName()).isEqualTo("Distribuidora Norte");
        assertThat(presenter.getBusinessName()).isEqualTo("Distribuidora Norte");
    }

    @Test
    void saveRejectsBlankName() {
        assertThatThrownBy(() -> presenter.save("   ", null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre del negocio es obligatorio.");
        assertThatThrownBy(() -> presenter.save(null, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveRejectsTooLongName() {
        String tooLong = "x".repeat(BusinessSettingsPresenter.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> presenter.save(tooLong, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(BusinessSettingsPresenter.MAX_NAME_LENGTH));
    }

    @Test
    void saveStoresTheChosenLogoAtTheDataDirLocation() throws Exception {
        Path source = image(dataDir.resolve("mi-logo.png"));

        presenter.save("Mi negocio", source, false);

        assertThat(presenter.getLogoPath()).isEqualTo(LogoUtils.logoPathFor(dataDir));
        assertThat(presenter.getLogoPath()).exists();
    }

    @Test
    void saveWithoutNewLogoKeepsTheExistingOne() throws Exception {
        presenter.save("Mi negocio", image(dataDir.resolve("mi-logo.png")), false);
        byte[] before = Files.readAllBytes(presenter.getLogoPath());

        presenter.save("Otro nombre", null, false);

        assertThat(Files.readAllBytes(presenter.getLogoPath())).containsExactly(before);
    }

    @Test
    void saveWithRemoveFlagDeletesTheLogo() throws Exception {
        presenter.save("Mi negocio", image(dataDir.resolve("mi-logo.png")), false);

        presenter.save("Mi negocio", null, true);

        assertThat(presenter.getLogoPath()).isNull();
    }

    @Test
    void invalidLogoFailsWithoutChangingTheName() throws Exception {
        presenter.save("Nombre original", null, false);
        Path notAnImage = dataDir.resolve("texto.png");
        Files.writeString(notAnImage, "no soy una imagen");

        assertThatThrownBy(() -> presenter.save("Nombre nuevo", notAnImage, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("logo");

        assertThat(config.getBusinessName()).isEqualTo("Nombre original");
    }

    private static Path image(Path target) throws IOException {
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", target.toFile());
        return target;
    }
}
