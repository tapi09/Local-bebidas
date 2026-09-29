package com.softwaredebebidas.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoUtilsTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("softwaredebebidas-test");
        System.setProperty("user.home", tempDir.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .map(Path::toFile)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(File::delete);
        }
    }

    @Test
    void copyInvoiceImageCreatesPurchaseInvoicesDirectory() throws IOException {
        Path sourceImage = tempDir.resolve("source.jpg");
        Files.write(sourceImage, new byte[]{0x00, 0x01, 0x02});

        String result = PhotoUtils.copyInvoiceImage(sourceImage.toFile());

        assertThat(result).startsWith("purchase-invoices/");
        assertThat(result).endsWith(".jpg");

        Path absolutePath = PhotoUtils.resolvePath(result);
        assertThat(absolutePath).isNotNull();
        assertThat(Files.exists(absolutePath)).isTrue();
    }

    @Test
    void copyInvoiceImageReturnsValidRelativePath() throws IOException {
        Path sourceImage = tempDir.resolve("factura.png");
        Files.write(sourceImage, new byte[]{0x00, 0x01});

        String result = PhotoUtils.copyInvoiceImage(sourceImage.toFile());

        assertThat(result).matches("purchase-invoices/[a-f0-9-]+\\.png");
    }

    @Test
    void copyInvoiceImagePreservesOriginalExtension() throws IOException {
        Path sourceJpg = tempDir.resolve("invoice.jpg");
        Files.write(sourceJpg, new byte[]{0x00, 0x01});

        String jpgResult = PhotoUtils.copyInvoiceImage(sourceJpg.toFile());
        assertThat(jpgResult).endsWith(".jpg");

        Path sourcePng = tempDir.resolve("invoice.png");
        Files.write(sourcePng, new byte[]{0x00, 0x01});

        String pngResult = PhotoUtils.copyInvoiceImage(sourcePng.toFile());
        assertThat(pngResult).endsWith(".png");
    }

    @Test
    void copyInvoiceImageGeneratesUniqueFilenames() throws IOException {
        Path sourceImage = tempDir.resolve("source.jpg");
        Files.write(sourceImage, new byte[]{0x00, 0x01});

        String result1 = PhotoUtils.copyInvoiceImage(sourceImage.toFile());
        String result2 = PhotoUtils.copyInvoiceImage(sourceImage.toFile());

        assertThat(result1).isNotEqualTo(result2);
    }
}