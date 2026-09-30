package com.softwaredebebidas.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppDataDirTest {

    @AfterEach
    void resetInitializedDir() {
        AppDataDir.reset();
    }

    @Test
    void namesAreTheGenericProductNames() {
        assertThat(AppDataDir.DIR_NAME).isEqualTo("software-bebidas");
        assertThat(AppDataDir.DB_FILE).isEqualTo("software-bebidas.db");
        assertThat(AppDataDir.LOCK_FILE).isEqualTo("software-bebidas.lock");
    }

    @Test
    void defaultDirLivesUnderAppDataWhenSet() {
        Path result = AppDataDir.defaultDir("C:/Users/x/AppData/Roaming");

        assertThat(result).isEqualTo(Path.of("C:/Users/x/AppData/Roaming", "software-bebidas"));
    }

    @Test
    void defaultDirFallsBackToLocalDataFolderWhenAppDataIsUnset() {
        assertThat(AppDataDir.defaultDir(null)).isEqualTo(Paths.get("data"));
        assertThat(AppDataDir.defaultDir("  ")).isEqualTo(Paths.get("data"));
    }

    @Test
    void initializeMakesTheResolvedDirTheSingleSourceOfTruth(@TempDir Path root) {
        Path result = AppDataDir.initialize(root);

        assertThat(result).isEqualTo(root.resolve(AppDataDir.DIR_NAME));
        assertThat(AppDataDir.getBaseDir()).isEqualTo(result);
    }

    @Test
    void databaseAndLockFilesLiveInsideTheDataDir(@TempDir Path dir) {
        assertThat(AppDataDir.databaseFile(dir)).isEqualTo(dir.resolve("software-bebidas.db"));
        assertThat(AppDataDir.lockFile(dir)).isEqualTo(dir.resolve("software-bebidas.lock"));
    }

    @Test
    void needsSetupUntilTheDatabaseFileExists(@TempDir Path dir) throws IOException {
        assertThat(AppDataDir.needsSetup(dir)).isTrue();

        Files.writeString(AppDataDir.databaseFile(dir), "x");

        assertThat(AppDataDir.needsSetup(dir)).isFalse();
    }

    @Test
    void importFromCopiesTheTreeRenamesTheDbAndSkipsLocks(@TempDir Path src, @TempDir Path dest)
            throws IOException {
        Files.writeString(src.resolve("old-name.db"), "data");
        Files.writeString(src.resolve("old-name.lock"), "lock");
        Files.createDirectories(src.resolve("photos"));
        Files.writeString(src.resolve("photos/a.png"), "img");

        AppDataDir.importFrom(src, dest);

        assertThat(Files.readString(AppDataDir.databaseFile(dest))).isEqualTo("data");
        assertThat(dest.resolve("old-name.db")).doesNotExist();
        assertThat(dest.resolve("old-name.lock")).doesNotExist();
        assertThat(Files.readString(dest.resolve("photos/a.png"))).isEqualTo("img");
        assertThat(src.resolve("old-name.db")).exists();
        assertThat(src.resolve("old-name.lock")).exists();
    }

    @Test
    void importFromRenamesTheSqliteCompanionFilesWithTheDb(@TempDir Path src, @TempDir Path dest)
            throws IOException {
        Files.writeString(src.resolve("old-name.db"), "data");
        Files.writeString(src.resolve("old-name.db-wal"), "wal");
        Files.writeString(src.resolve("old-name.db-shm"), "shm");

        AppDataDir.importFrom(src, dest);

        String db = AppDataDir.databaseFile(dest).toString();
        assertThat(Files.readString(Path.of(db + "-wal"))).isEqualTo("wal");
        assertThat(Files.readString(Path.of(db + "-shm"))).isEqualTo("shm");
        assertThat(dest.resolve("old-name.db-wal")).doesNotExist();
    }

    @Test
    void importFromRejectsAFolderWithoutExactlyOneDb(@TempDir Path src, @TempDir Path dest)
            throws IOException {
        assertThatThrownBy(() -> AppDataDir.importFrom(src, dest)).isInstanceOf(IOException.class);

        Files.writeString(src.resolve("a.db"), "1");
        Files.writeString(src.resolve("b.db"), "2");
        assertThatThrownBy(() -> AppDataDir.importFrom(src, dest)).isInstanceOf(IOException.class);
        assertThat(AppDataDir.needsSetup(dest)).isTrue();
    }
}
