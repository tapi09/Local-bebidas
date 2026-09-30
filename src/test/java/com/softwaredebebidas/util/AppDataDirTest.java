package com.softwaredebebidas.util;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class AppDataDirTest {

    @Test
    void migratesLegacyDirectoryWhenTargetIsMissing(@TempDir Path root) throws IOException {
        Path legacy = Files.createDirectories(root.resolve(AppDataDir.LEGACY_DIR_NAME));
        Files.writeString(legacy.resolve(AppDataDir.LEGACY_DB_FILE), "db-bytes");
        Files.writeString(legacy.resolve(AppDataDir.LEGACY_DB_FILE + "-wal"), "wal-bytes");
        Files.writeString(legacy.resolve(AppDataDir.LEGACY_LOCK_FILE), "");
        Files.createDirectories(legacy.resolve("backups"));
        Files.writeString(legacy.resolve("backups").resolve("old_backup.db"), "backup");
        Files.createDirectories(legacy.resolve("product-photos"));
        Files.writeString(legacy.resolve("product-photos").resolve("a.jpg"), "photo");

        Path result = AppDataDir.migrateLegacy(root);

        Path target = root.resolve(AppDataDir.DIR_NAME);
        assertThat(result).isEqualTo(target);
        assertThat(target.resolve(AppDataDir.DB_FILE)).hasContent("db-bytes");
        assertThat(target.resolve(AppDataDir.DB_FILE + "-wal")).hasContent("wal-bytes");
        assertThat(target.resolve("backups").resolve("old_backup.db")).hasContent("backup");
        assertThat(target.resolve("product-photos").resolve("a.jpg")).hasContent("photo");
        assertThat(target.resolve(AppDataDir.LEGACY_DB_FILE)).doesNotExist();
        assertThat(target.resolve(AppDataDir.LEGACY_LOCK_FILE)).doesNotExist();
        // The legacy directory is kept untouched as a safety copy.
        assertThat(legacy.resolve(AppDataDir.LEGACY_DB_FILE)).hasContent("db-bytes");
        assertThat(root.resolve(AppDataDir.DIR_NAME + AppDataDir.STAGING_SUFFIX)).doesNotExist();
    }

    @Test
    void keepsExistingTargetAndIgnoresLegacy(@TempDir Path root) throws IOException {
        Path legacy = Files.createDirectories(root.resolve(AppDataDir.LEGACY_DIR_NAME));
        Files.writeString(legacy.resolve(AppDataDir.LEGACY_DB_FILE), "old");
        Path target = Files.createDirectories(root.resolve(AppDataDir.DIR_NAME));
        Files.writeString(target.resolve(AppDataDir.DB_FILE), "current");

        Path result = AppDataDir.migrateLegacy(root);

        assertThat(result).isEqualTo(target);
        assertThat(target.resolve(AppDataDir.DB_FILE)).hasContent("current");
    }

    @Test
    void freshInstallUsesTargetWithoutCreatingLegacy(@TempDir Path root) {
        Path result = AppDataDir.migrateLegacy(root);

        assertThat(result).isEqualTo(root.resolve(AppDataDir.DIR_NAME));
        assertThat(root.resolve(AppDataDir.LEGACY_DIR_NAME)).doesNotExist();
    }

    @Test
    void discardsStaleStagingDirectoryFromInterruptedMigration(@TempDir Path root) throws IOException {
        Path legacy = Files.createDirectories(root.resolve(AppDataDir.LEGACY_DIR_NAME));
        Files.writeString(legacy.resolve(AppDataDir.LEGACY_DB_FILE), "db-bytes");
        Path staging = Files.createDirectories(root.resolve(AppDataDir.DIR_NAME + AppDataDir.STAGING_SUFFIX));
        Files.writeString(staging.resolve("partial.tmp"), "junk");

        Path result = AppDataDir.migrateLegacy(root);

        assertThat(result.resolve(AppDataDir.DB_FILE)).hasContent("db-bytes");
        assertThat(result.resolve("partial.tmp")).doesNotExist();
        assertThat(staging).doesNotExist();
    }

    @Test
    void fallsBackToLegacyDirectoryWhenCopyFails(@TempDir Path root) throws IOException {
        Path legacy = Files.createDirectories(root.resolve(AppDataDir.LEGACY_DIR_NAME));
        Files.writeString(legacy.resolve(AppDataDir.LEGACY_DB_FILE), "db-bytes");

        Path result = AppDataDir.migrateLegacy(root, (from, to) -> {
            throw new IOException("disk full");
        });

        assertThat(result).isEqualTo(legacy);
        assertThat(root.resolve(AppDataDir.DIR_NAME)).doesNotExist();
        assertThat(root.resolve(AppDataDir.DIR_NAME + AppDataDir.STAGING_SUFFIX)).doesNotExist();
        assertThat(AppDataDir.databaseFile(result)).isEqualTo(legacy.resolve(AppDataDir.LEGACY_DB_FILE));
    }

    @Test
    void databaseFilePrefersCurrentNameAndFallsBackToLegacy(@TempDir Path dir) throws IOException {
        assertThat(AppDataDir.databaseFile(dir)).isEqualTo(dir.resolve(AppDataDir.DB_FILE));

        Files.writeString(dir.resolve(AppDataDir.LEGACY_DB_FILE), "old");
        assertThat(AppDataDir.databaseFile(dir)).isEqualTo(dir.resolve(AppDataDir.LEGACY_DB_FILE));

        Files.writeString(dir.resolve(AppDataDir.DB_FILE), "new");
        assertThat(AppDataDir.databaseFile(dir)).isEqualTo(dir.resolve(AppDataDir.DB_FILE));
    }

    @Test
    void upgradedInstallationKeepsItsRealDatabaseData(@TempDir Path root) throws SQLException {
        // Simulates an existing 1.0.3 installation writing to the legacy location.
        Path legacyDb = root.resolve(AppDataDir.LEGACY_DIR_NAME).resolve(AppDataDir.LEGACY_DB_FILE);
        legacyDb.getParent().toFile().mkdirs();
        DatabaseManager oldInstall = DatabaseManager.createFromFile(legacyDb.toString());
        Product product = new Product();
        product.setName("Producto existente");
        product.setCategoryName("Gaseosas");
        product.setPresentation("Botella");
        product.setCostPrice(100.0);
        product.setSalePrice(200.0);
        product.setMinStock(5);
        product.setActive(true);
        new ProductRepository(oldInstall).save(product);
        oldInstall.close();

        // First start of the upgraded version.
        Path dataDir = AppDataDir.migrateLegacy(root);
        DatabaseManager upgraded = DatabaseManager.createFromFile(AppDataDir.databaseFile(dataDir).toString());

        assertThat(dataDir).isEqualTo(root.resolve(AppDataDir.DIR_NAME));
        assertThat(new ProductRepository(upgraded).findAllActive())
                .extracting(Product::getName)
                .contains("Producto existente");
        upgraded.close();
    }
}
