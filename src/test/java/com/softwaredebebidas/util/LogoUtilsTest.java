package com.softwaredebebidas.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogoUtilsTest {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};

    @TempDir
    Path tempDir;

    @Test
    void logoPathForBuildsLogoSubpathUnderBaseDir() {
        Path result = LogoUtils.logoPathFor(Path.of("appdata", "AppDir"));

        assertThat(result).isEqualTo(Path.of("appdata", "AppDir", "logo", "logo.png"));
    }

    @Test
    void resolveLogoPathReturnsDataDirLogoWhenPresent() throws Exception {
        Path dataLogo = Files.createDirectories(tempDir.resolve("logo")).resolve("logo.png");
        Files.write(dataLogo, new byte[]{1});

        assertThat(LogoUtils.resolveLogoPath(tempDir)).isEqualTo(dataLogo);
    }

    @Test
    void resolveLogoPathReturnsNullWhenThereIsNoLogo() {
        assertThat(LogoUtils.resolveLogoPath(tempDir)).isNull();
    }

    @Test
    void saveLogoStoresPngAtTheLogoLocation() throws Exception {
        Path source = writeImage(tempDir.resolve("origen.png"), "png", 20, 10);

        LogoUtils.saveLogo(tempDir, source);

        Path target = LogoUtils.logoPathFor(tempDir);
        assertThat(target).exists();
        assertThat(Files.readAllBytes(target)).startsWith(PNG_MAGIC);
    }

    @Test
    void saveLogoNormalizesJpgToPng() throws Exception {
        Path source = writeImage(tempDir.resolve("origen.jpg"), "jpg", 20, 10);

        LogoUtils.saveLogo(tempDir, source);

        Path target = LogoUtils.logoPathFor(tempDir);
        assertThat(Files.readAllBytes(target)).startsWith(PNG_MAGIC);
        BufferedImage image = ImageIO.read(target.toFile());
        assertThat(image.getWidth()).isEqualTo(20);
        assertThat(image.getHeight()).isEqualTo(10);
    }

    @Test
    void saveLogoReplacesThePreviousLogo() throws Exception {
        LogoUtils.saveLogo(tempDir, writeImage(tempDir.resolve("a.png"), "png", 20, 10));
        Path second = writeImage(tempDir.resolve("b.png"), "png", 40, 30);

        LogoUtils.saveLogo(tempDir, second);

        BufferedImage stored = ImageIO.read(LogoUtils.logoPathFor(tempDir).toFile());
        assertThat(stored.getWidth()).isEqualTo(40);
    }

    @Test
    void saveLogoRejectsFilesThatAreNotImagesAndKeepsTheCurrentLogo() throws Exception {
        LogoUtils.saveLogo(tempDir, writeImage(tempDir.resolve("a.png"), "png", 20, 10));
        byte[] before = Files.readAllBytes(LogoUtils.logoPathFor(tempDir));
        Path notAnImage = tempDir.resolve("texto.png");
        Files.write(notAnImage, "no soy una imagen".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> LogoUtils.saveLogo(tempDir, notAnImage))
                .isInstanceOf(IOException.class);

        assertThat(Files.readAllBytes(LogoUtils.logoPathFor(tempDir))).containsExactly(before);
    }

    @Test
    void removeLogoDeletesTheStoredLogo() throws Exception {
        LogoUtils.saveLogo(tempDir, writeImage(tempDir.resolve("a.png"), "png", 20, 10));

        LogoUtils.removeLogo(tempDir);

        assertThat(LogoUtils.logoPathFor(tempDir)).doesNotExist();
        assertThat(LogoUtils.resolveLogoPath(tempDir)).isNull();
    }

    @Test
    void removeLogoIsANoOpWhenThereIsNoLogo() throws Exception {
        LogoUtils.removeLogo(tempDir);

        assertThat(LogoUtils.logoPathFor(tempDir)).doesNotExist();
    }

    @Test
    void theApplicationNeverBundlesABrandLogo() {
        assertThat(LogoUtils.class.getResource("/icons/logo.png")).isNull();
        assertThat(LogoUtils.class.getResource("/icons/logo_secundario_gpt.png")).isNull();
        assertThat(LogoUtils.class.getResource("/icons/softwaredebebidas.png")).isNotNull();
    }

    private static Path writeImage(Path target, String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, format, target.toFile());
        return target;
    }
}
