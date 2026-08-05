package com.cocolatan.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LogoUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void logoPathForBuildsLogoSubpathUnderBaseDir() {
        Path result = LogoUtils.logoPathFor(Path.of("appdata", "Cocolatan"));

        assertThat(result).isEqualTo(Path.of("appdata", "Cocolatan", "logo", "logo.png"));
    }

    @Test
    void resolveLogoPathPrefersDataDirLogoOverLegacy() throws Exception {
        Path dataLogo = Files.createDirectories(tempDir.resolve("logo"))
                .resolve("logo.png");
        Files.write(dataLogo, new byte[]{1});
        Path legacyLogo = Files.createDirectories(tempDir.resolve("legacy").resolve("logo"))
                .resolve("logo.png");
        Files.write(legacyLogo, new byte[]{2});

        Path result = LogoUtils.resolveLogoPath(tempDir, legacyLogo);

        assertThat(result).isEqualTo(dataLogo);
    }

    @Test
    void resolveLogoPathFallsBackToLegacyWhenDataDirMissing() throws Exception {
        Path legacyLogo = Files.createDirectories(tempDir.resolve("legacy").resolve("logo"))
                .resolve("logo.png");
        Files.write(legacyLogo, new byte[]{3});

        Path result = LogoUtils.resolveLogoPath(tempDir, legacyLogo);

        assertThat(result).isEqualTo(legacyLogo);
    }

    @Test
    void resolveLogoPathReturnsNullWhenNeitherExists() {
        Path missingLegacy = tempDir.resolve("nonexistent").resolve("logo.png");

        Path result = LogoUtils.resolveLogoPath(tempDir, missingLegacy);

        assertThat(result).isNull();
    }

    @Test
    void ensureLogoCopiesBundledStreamWhenMissing() throws Exception {
        byte[] pngBytes = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
        InputStream bundled = new ByteArrayInputStream(pngBytes);

        boolean result = LogoUtils.ensureLogo(tempDir, bundled);

        assertThat(result).isTrue();
        Path target = LogoUtils.logoPathFor(tempDir);
        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.readAllBytes(target)).containsExactly(pngBytes);
    }

    @Test
    void ensureLogoOverwritesWhenBundledDiffers() throws Exception {
        Path target = Files.createDirectories(tempDir.resolve("logo"))
                .resolve("logo.png");
        byte[] existing = "already-present".getBytes(StandardCharsets.UTF_8);
        Files.write(target, existing);
        byte[] newBytes = "updated-logo".getBytes(StandardCharsets.UTF_8);
        InputStream bundled = new ByteArrayInputStream(newBytes);

        boolean result = LogoUtils.ensureLogo(tempDir, bundled);

        assertThat(result).isTrue();
        assertThat(Files.readAllBytes(target)).containsExactly(newBytes);
    }

    @Test
    void ensureLogoKeepsIdenticalFileWhenBundledMatches() throws Exception {
        Path target = Files.createDirectories(tempDir.resolve("logo"))
                .resolve("logo.png");
        byte[] existing = "same-logo".getBytes(StandardCharsets.UTF_8);
        Files.write(target, existing);
        InputStream bundled = new ByteArrayInputStream(existing);

        boolean result = LogoUtils.ensureLogo(tempDir, bundled);

        assertThat(result).isTrue();
        assertThat(Files.readAllBytes(target)).containsExactly(existing);
    }

    @Test
    void ensureLogoReturnsFalseWhenBundledStreamMissing() {
        boolean result = LogoUtils.ensureLogo(tempDir, null);

        assertThat(result).isFalse();
        assertThat(Files.exists(LogoUtils.logoPathFor(tempDir))).isFalse();
    }

    @Test
    void bundledLogoStreamProvidesBundledIconResource() throws Exception {
        InputStream stream = LogoUtils.bundledLogoStream();

        assertThat(stream).isNotNull();
        assertThat(stream.read()).isNotEqualTo(-1);
    }
}
