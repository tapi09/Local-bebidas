package com.cocolatan.integration;

import com.cocolatan.model.Product;
import com.cocolatan.model.Sale;
import com.cocolatan.model.SaleItem;
import com.cocolatan.model.StockMovement;
import com.cocolatan.model.Supplier;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.SaleRepository;
import com.cocolatan.repository.StockMovementRepository;
import com.cocolatan.repository.SupplierRepository;
import com.cocolatan.service.InventoryService;
import com.cocolatan.service.ReceiptService;
import com.cocolatan.service.SalesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the cancellation race fix (REQ-CANCEL-01).
 *
 * <p>Deviation note: no TestFX harness in this repo (see
 * {@code BackupExportIntegrationTest}); the race is exercised against the real
 * stack (real SQLite database, real repositories, real {@link SalesService})
 * with two threads hitting the same sale simultaneously.</p>
 *
 * <p>Concurrency setup: two {@link DatabaseManager} instances open separate
 * connections to the SAME file database, one per racing thread. SQLite WAL mode
 * serializes writers, so exactly one of the two cancels can commit; the loser
 * either re-reads {@code CANCELLED} inside its transaction and throws
 * {@link IllegalStateException}, or is rejected by the write lock. In every
 * interleaving the invariant holds: exactly ONE set of stock replenishment
 * movements is persisted.</p>
 */
class CancellationRaceIntegrationTest {

    private static final long PRODUCT_ID = 1L;
    private static final int ITEM_COUNT = 2;

    private Path dbFile;
    private DatabaseManager managerA;
    private DatabaseManager managerB;
    private SaleRepository saleRepoA;
    private StockMovementRepository stockRepoA;
    private SalesService serviceA;
    private SalesService serviceB;
    private Long saleId;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("cancellation-race", ".db");
        // Two independent connections to the same file → real concurrency
        managerA = DatabaseManager.createFromFile(dbFile.toString());
        managerB = DatabaseManager.createFromFile(dbFile.toString());

        // Seed supplier + product + opening stock via manager A
        SupplierRepository supplierRepo = new SupplierRepository(managerA);
        Supplier supplier = new Supplier();
        supplier.setName("Distribuidora Norte");
        supplierRepo.save(supplier);

        ProductRepository productRepoA = new ProductRepository(managerA);
        Product coke = new Product();
        coke.setName("Coca-Cola 500ml");
        coke.setCategory("Gaseosas");
        coke.setPresentation("Botella 500ml");
        coke.setCostPrice(350.0);
        coke.setSalePrice(600.0);
        coke.setSupplierId(1L);
        coke.setBarcode("7790001001");
        coke.setMinStock(5);
        productRepoA.save(coke);

        stockRepoA = new StockMovementRepository(managerA);
        StockMovement entry = new StockMovement();
        entry.setProductId(PRODUCT_ID);
        entry.setMovementType("ENTRY");
        entry.setQuantity(24);
        entry.setReferenceType("PURCHASE");
        entry.setReferenceId(1L);
        stockRepoA.insert(entry);

        // Create one sale with two items (one replenishment set = 2 movements)
        saleRepoA = new SaleRepository(managerA);
        Sale sale = new Sale();
        sale.setSaleDate("22/07/2026");
        sale.setChannel("IN");
        sale.setPaymentMethod("CASH");
        List<SaleItem> items = Arrays.asList(
                createItem(PRODUCT_ID, 2, 600.0),
                createItem(PRODUCT_ID, 1, 600.0)
        );
        saleId = saleRepoA.saveWithItems(sale, items);

        // Two services, each bound to its own connection
        InventoryService inventoryA = new InventoryService(stockRepoA, productRepoA);
        ReceiptService receiptA = new ReceiptService(productRepoA);
        serviceA = new SalesService(saleRepoA, stockRepoA, productRepoA, inventoryA, receiptA, managerA);

        StockMovementRepository stockRepoB = new StockMovementRepository(managerB);
        ProductRepository productRepoB = new ProductRepository(managerB);
        SaleRepository saleRepoB = new SaleRepository(managerB);
        InventoryService inventoryB = new InventoryService(stockRepoB, productRepoB);
        ReceiptService receiptB = new ReceiptService(productRepoB);
        serviceB = new SalesService(saleRepoB, stockRepoB, productRepoB, inventoryB, receiptB, managerB);
    }

    @AfterEach
    void tearDown() throws IOException {
        managerA.close();
        managerB.close();
        Files.deleteIfExists(dbFile);
        Files.deleteIfExists(Path.of(dbFile + "-wal"));
        Files.deleteIfExists(Path.of(dbFile + "-shm"));
    }

    // ──────────────────────────────────────────────
    // Sequential double-cancel — idempotent error path
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Segunda cancelación secuencial lanza IllegalStateException y no duplica movimientos")
    void sequentialDoubleCancelIsIdempotent() throws SQLException {
        serviceA.cancelSale(saleId, "Motivo test");

        assertThatThrownBy(() -> serviceA.cancelSale(saleId, "Segundo intento"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Sale already cancelled");

        assertThat(saleEntryMovementCount()).isEqualTo(ITEM_COUNT);
        assertThat(saleRepoA.findById(saleId).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }

    // ──────────────────────────────────────────────
    // Concurrent double-cancel — the race itself
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("Dos cancelaciones concurrentes: exactamente UN conjunto de movimientos y un solo ganador")
    void concurrentCancelCreatesExactlyOneMovementSet() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successes = new AtomicInteger();

        Thread t1 = new Thread(() -> runCancel(serviceA, start, successes, failures));
        Thread t2 = new Thread(() -> runCancel(serviceB, start, successes, failures));
        t1.start();
        t2.start();
        start.countDown();
        t1.join(15_000);
        t2.join(15_000);

        // No deadlock — both threads finished
        assertThat(t1.isAlive() || t2.isAlive()).isFalse();
        // Exactly one of the two racing calls committed
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(RuntimeException.class);
        // Exactly ONE set of stock movements was persisted (never two)
        assertThat(saleEntryMovementCount()).isEqualTo(ITEM_COUNT);
        // The sale ends cancelled
        assertThat(saleRepoA.findById(saleId).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }

    // ──────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────

    private void runCancel(SalesService svc, CountDownLatch start,
                           AtomicInteger successes, List<Throwable> failures) {
        try {
            start.await();
            svc.cancelSale(saleId, "Carrera");
            successes.incrementAndGet();
        } catch (Throwable t) {
            failures.add(t);
        }
    }

    private SaleItem createItem(Long productId, int quantity, double unitPrice) {
        SaleItem item = new SaleItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        return item;
    }

    private long saleEntryMovementCount() throws SQLException {
        return stockRepoA.findByProductIdAndType(PRODUCT_ID, "ENTRY").stream()
                .filter(m -> "SALE".equals(m.getReferenceType()) && saleId.equals(m.getReferenceId()))
                .count();
    }
}
