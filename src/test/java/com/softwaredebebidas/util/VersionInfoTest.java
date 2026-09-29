package com.softwaredebebidas.util;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class VersionInfoTest {

    @Test
    void fromPropertiesReturnsVersionAndBuildDateForCompleteProperties() {
        Properties props = new Properties();
        props.setProperty("app.version", "1.0.0");
        props.setProperty("app.build.date", "2026-08-03 10:00");

        VersionInfo info = VersionInfo.fromProperties(props);

        assertThat(info.version()).isEqualTo("1.0.0");
        assertThat(info.buildDate()).isEqualTo("2026-08-03 10:00");
        assertThat(info.displayString()).isEqualTo("1.0.0 (2026-08-03 10:00)");
    }

    @Test
    void fromPropertiesFallsBackToUnknownForNullProperties() {
        VersionInfo info = VersionInfo.fromProperties(null);

        assertThat(info.version()).isEqualTo("unknown");
        assertThat(info.buildDate()).isEqualTo("unknown");
        assertThat(info.displayString()).isEqualTo("unknown");
    }

    @Test
    void fromPropertiesFallsBackToUnknownForMissingBuildDate() {
        Properties props = new Properties();
        props.setProperty("app.version", "1.0.0");

        VersionInfo info = VersionInfo.fromProperties(props);

        assertThat(info.version()).isEqualTo("1.0.0");
        assertThat(info.buildDate()).isEqualTo("unknown");
        assertThat(info.displayString()).isEqualTo("1.0.0");
    }

    @Test
    void fromPropertiesFallsBackToUnknownForBlankValues() {
        Properties props = new Properties();
        props.setProperty("app.version", "   ");
        props.setProperty("app.build.date", "");

        VersionInfo info = VersionInfo.fromProperties(props);

        assertThat(info.version()).isEqualTo("unknown");
        assertThat(info.buildDate()).isEqualTo("unknown");
    }

    @Test
    void fromPropertiesToleratesUnexpectedEntriesWithoutThrowing() {
        Properties props = new Properties();
        props.setProperty("app.version", "1.0.0-SNAPSHOT");
        props.setProperty("garbage.key", "not-a-date @@");

        assertThatCode(() -> {
            VersionInfo info = VersionInfo.fromProperties(props);
            assertThat(info.version()).isEqualTo("1.0.0-SNAPSHOT");
            assertThat(info.buildDate()).isEqualTo("unknown");
        }).doesNotThrowAnyException();
    }

    @Test
    void fromPropertiesTrimsValues() {
        Properties props = new Properties();
        props.setProperty("app.version", " 2.1.0 ");
        props.setProperty("app.build.date", " 2026-08-03 11:30 ");

        VersionInfo info = VersionInfo.fromProperties(props);

        assertThat(info.version()).isEqualTo("2.1.0");
        assertThat(info.buildDate()).isEqualTo("2026-08-03 11:30");
    }

    @Test
    void staticGettersNeverThrowEvenWhenClasspathMetadataIsMissing() {
        assertThatCode(() -> {
            String version = VersionInfo.getVersion();
            String buildDate = VersionInfo.getBuildDate();
            String display = VersionInfo.getDisplayString();
            assertThat(version).isNotNull();
            assertThat(buildDate).isNotNull();
            assertThat(display).isNotNull();
        }).doesNotThrowAnyException();
    }
}
