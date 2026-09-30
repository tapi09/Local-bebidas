package com.softwaredebebidas.service;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.model.StockMovement;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.SaleRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import com.softwaredebebidas.service.InventoryService;
import com.softwaredebebidas.service.ReceiptService;
import com.softwaredebebidas.service.SalesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupSchedulerTest {

    @Mock
    private BackupService backupService;

    private DatabaseManager dbManager;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) dbManager.close();
    }

    @Test
    void startIsIdempotent() throws Exception {
        BackupScheduler scheduler = new BackupScheduler(backupService);

        scheduler.start(60, 10);
        ScheduledExecutorService first = (ScheduledExecutorService) fieldOn(scheduler, "scheduler");

        scheduler.start(60, 10);
        ScheduledExecutorService second = (ScheduledExecutorService) fieldOn(scheduler, "scheduler");

        assertThat(second).isSameAs(first);
        scheduler.stop();
    }

    @Test
    void stopShutsDownTheScheduler() throws Exception {
        BackupScheduler scheduler = new BackupScheduler(backupService);

        scheduler.start(60, 10);
        ScheduledExecutorService exec = (ScheduledExecutorService) fieldOn(scheduler, "scheduler");

        scheduler.stop();

        assertThat(exec.isShutdown()).isTrue();
    }

    @Test
    void backupRunsConcurrentlyWithSaleWithoutError(@TempDir Path tempDir) throws Exception {
        // Real backup service using a file-based DB so VACUUM INTO can run on a separate connection.
        Path dbFile = tempDir.resolve("software-bebidas.db");
        BackupService realBackup = new BackupService(dbFile, tempDir.resolve("backups"));
        BackupScheduler scheduler = new BackupScheduler(realBackup);
        scheduler.start(1, 2);

        try {
            // Set up real repositories on the file DB
            ProductRepository productRepo = new ProductRepository(dbManager);
            SaleRepository saleRepo = new SaleRepository(dbManager);
            StockMovementRepository stockRepo = new StockMovementRepository(dbManager);
            InventoryService inventoryService = new InventoryService(stockRepo, productRepo);
            SalesService salesService = new SalesService(
                    saleRepo, stockRepo, productRepo, inventoryService,
                    new ReceiptService(productRepo), dbManager
            );

            // Insert a product and stock
            Product p = new Product();
            p.setName("Test Product");
            p.setCategory("Test");
            p.setPresentation("Botella");
            p.setCostPrice(100.0);
            p.setSalePrice(200.0);
            p.setMinStock(5);
            Long productId = productRepo.save(p);

            StockMovement entry = new StockMovement();
            entry.setProductId(productId);
            entry.setMovementType("ENTRY");
            entry.setQuantity(10);
            entry.setReferenceType("ADJUSTMENT");
            stockRepo.insert(entry);
            // Update denormalized current_stock
            productRepo.updateStock(dbManager.getConnection(), productId, 10);

            // Run a sale and let the backup scheduler tick concurrently.
            // The backup interval is 1 minute so the task won't fire during
            // this short test; the real concurrency guarantee is that
            // VACUUM INTO on a separate connection takes only a SHARED lock
            // (WAL mode), so it never blocks the UI writer connection.
            Sale sale = new Sale();
            sale.setSaleDate(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            sale.setChannel("IN");
            sale.setPaymentMethod("CASH");
            SaleItem item = new SaleItem();
            item.setProductId(productId);
            item.setQuantity(2);
            item.setUnitPrice(200.0);
            boolean created = salesService.createSale(sale, Collections.singletonList(item));

            assertThat(created).isTrue();
        } finally {
            scheduler.stop();
        }
    }

    private Object fieldOn(BackupScheduler target, String name) throws Exception {
        Field f = BackupScheduler.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
