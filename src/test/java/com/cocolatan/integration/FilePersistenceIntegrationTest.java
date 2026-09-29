package com.cocolatan.integration;

import com.cocolatan.model.Product;
import com.cocolatan.model.Sale;
import com.cocolatan.model.SaleItem;
import com.cocolatan.model.StockMovement;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.SaleRepository;
import com.cocolatan.repository.StockMovementRepository;
import com.cocolatan.service.InventoryService;
import com.cocolatan.service.ReceiptService;
import com.cocolatan.service.SalesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reproduces the real "close the app / reopen the app" scenario against an
 * actual file-backed SQLite database (createFromFile), not createInMemory().
 * Every other integration test in this project uses an in-memory connection,
 * which never proves anything survives a real close+reopen of the .db file.
 */
class FilePersistenceIntegrationTest {

    @Test
    void productSaleAndStockAdjustmentSurviveCloseAndReopen(@TempDir Path tempDir) throws SQLException {
        String dbPath = tempDir.resolve("persistence-test.db").toString();

        DatabaseManager dbManager = DatabaseManager.createFromFile(dbPath);
        ProductRepository productRepository = new ProductRepository(dbManager);
        StockMovementRepository stockMovementRepository = new StockMovementRepository(dbManager);
        InventoryService inventoryService = new InventoryService(stockMovementRepository, productRepository);
        SaleRepository saleRepository = new SaleRepository(dbManager);
        ReceiptService receiptService = new ReceiptService(productRepository);
        SalesService salesService = new SalesService(saleRepository, stockMovementRepository, productRepository,
                inventoryService, receiptService, dbManager);

        Product product = new Product();
        product.setName("Producto Persistencia Test");
        product.setCategoryName("Gaseosas");
        product.setPresentation("Botella");
        product.setCostPrice(100.0);
        product.setSalePrice(200.0);
        product.setMinStock(5);
        product.setActive(true);
        Long productId = productRepository.save(product);

        // NOTE: NOT using InventoryService.adjustStock() here — a separate, already
        // confirmed bug (adjustStock never calls productRepository.updateStock()) would
        // leave current_stock at 0 while stock_movements shows 50. That mismatch then makes
        // SalesService.createSale's own updateStock(-10) drive current_stock negative and
        // hit the `CHECK (current_stock >= 0)` constraint, aborting the whole sale with a
        // RuntimeException — reproduced independently below as a documented finding, not
        // exercised here since it belongs to a different lane's bug (InventoryService).
        StockMovement initialEntry = new StockMovement();
        initialEntry.setProductId(productId);
        initialEntry.setMovementType("ENTRY");
        initialEntry.setQuantity(50);
        initialEntry.setReferenceType("ADJUSTMENT");
        initialEntry.setNotes("Carga inicial de test");
        stockMovementRepository.insert(initialEntry);
        productRepository.updateStock(dbManager.getConnection(), productId, 50);

        Sale sale = new Sale();
        sale.setChannel("IN");
        sale.setPaymentMethod("CASH");

        SaleItem item = new SaleItem();
        item.setProductId(productId);
        item.setQuantity(10);
        item.setUnitPrice(200.0);

        boolean created = salesService.createSale(sale, List.of(item));
        assertThat(created).isTrue();

        // NOTE: sale.getId() is null here — SalesService.createSale() never calls
        // sale.setId() on the caller's object (separate finding, documented in the test
        // below). Look the sale up through the repository instead, which is what the real
        // app's history/report screens do anyway.
        List<Sale> persistedSales = saleRepository.findAllActive();
        assertThat(persistedSales).hasSize(1);
        Long saleId = persistedSales.get(0).getId();
        assertThat(saleId).isNotNull();

        int stockBeforeClose = inventoryService.getCurrentStock(productId);

        dbManager.close();

        // Reopen the SAME file — the actual "close app / reopen app" scenario.
        DatabaseManager reopened = DatabaseManager.createFromFile(dbPath);
        ProductRepository reopenedProductRepo = new ProductRepository(reopened);
        SaleRepository reopenedSaleRepo = new SaleRepository(reopened);
        StockMovementRepository reopenedStockRepo = new StockMovementRepository(reopened);

        Optional<Product> reloadedProduct = reopenedProductRepo.findById(productId);
        assertThat(reloadedProduct).isPresent();
        assertThat(reloadedProduct.get().getName()).isEqualTo("Producto Persistencia Test");

        Optional<Sale> reloadedSale = reopenedSaleRepo.findById(saleId);
        assertThat(reloadedSale).isPresent();
        assertThat(reloadedSale.get().getTotalAmount()).isEqualTo(2000.0);

        int stockAfterReopen = reopenedStockRepo.computeCurrentStock(productId);
        assertThat(stockAfterReopen).isEqualTo(stockBeforeClose);
        assertThat(stockAfterReopen).isEqualTo(40); // 50 entry - 10 sold

        reopened.close();
    }

    /**
     * DOCUMENTED FINDING (not this lane's bug to fix — belongs to InventoryService.adjustStock,
     * already flagged separately): stock loaded through the real UI path for manual adjustments
     * used to make a SUBSEQUENT SALE OF THAT SAME STOCK FAIL WITH A RUNTIME EXCEPTION, on a
     * real file-backed database — not just "the dashboard counter is wrong", but "the sale of
     * that stock throws and never completes" (audit v3, B6).
     *
     * FIXED (audit v3, B2/B6): InventoryService.adjustStock() now calls
     * productRepository.updateStock(conn, productId, delta) in the same transaction as the
     * StockMovement insert, for both the ADJUSTMENT (increase) and EXIT (decrease) branches.
     * current_stock now stays in sync after a manual adjustment, so a later sale against that
     * stock no longer drives current_stock negative and no longer hits the CHECK constraint.
     */
    @Test
    void saleOfStockLoadedViaManualAdjustmentSucceedsAndKeepsCurrentStockInSync(@TempDir Path tempDir) throws SQLException {
        String dbPath = tempDir.resolve("adjust-then-sell-test.db").toString();

        DatabaseManager dbManager = DatabaseManager.createFromFile(dbPath);
        ProductRepository productRepository = new ProductRepository(dbManager);
        StockMovementRepository stockMovementRepository = new StockMovementRepository(dbManager);
        InventoryService inventoryService = new InventoryService(stockMovementRepository, productRepository);
        SaleRepository saleRepository = new SaleRepository(dbManager);
        ReceiptService receiptService = new ReceiptService(productRepository);
        SalesService salesService = new SalesService(saleRepository, stockMovementRepository, productRepository,
                inventoryService, receiptService, dbManager);

        Product product = new Product();
        product.setName("Producto Ajuste Manual Test");
        product.setCategoryName("Gaseosas");
        product.setPresentation("Botella");
        product.setCostPrice(100.0);
        product.setSalePrice(200.0);
        product.setMinStock(5);
        product.setActive(true);
        Long productId = productRepository.save(product);

        // The REAL path a cashier/admin uses from the Stock screen for a manual load.
        inventoryService.adjustStock(productId, 50, "Carga inicial via ajuste manual (flujo real de UI)");

        // The stock genuinely exists — validateStock (reading stock_movements) agrees.
        assertThat(inventoryService.validateStock(productId, 10)).isTrue();
        assertThat(inventoryService.getCurrentStock(productId)).isEqualTo(50);

        // products.current_stock now reflects the manual adjustment too (fixed, was 0 before).
        Optional<Product> afterAdjustment = productRepository.findById(productId);
        assertThat(afterAdjustment).isPresent();
        assertThat(afterAdjustment.get().getCurrentStock()).isEqualTo(50);

        Sale sale = new Sale();
        sale.setChannel("IN");
        sale.setPaymentMethod("CASH");
        SaleItem item = new SaleItem();
        item.setProductId(productId);
        item.setQuantity(10);
        item.setUnitPrice(200.0);

        // The sale succeeds — current_stock is in sync, so it no longer goes negative.
        assertThatCode(() -> salesService.createSale(sale, List.of(item))).doesNotThrowAnyException();

        Optional<Product> afterSale = productRepository.findById(productId);
        assertThat(afterSale).isPresent();
        assertThat(afterSale.get().getCurrentStock()).isEqualTo(40);

        dbManager.close();
    }

    /**
     * FIXED (audit v3, B7): SalesService.createSale() now calls sale.setId(saleId) right after
     * SaleRepository.saveWithItems() returns the generated id, so the caller's Sale object has
     * the real id immediately — e.g. for reprinting via SalePresenter.getLastReceipt(Long), or
     * navigating to it in history — instead of having to run a separate query for it.
     */
    @Test
    void createSalePopulatesGeneratedIdOnCallerSaleObject(@TempDir Path tempDir) throws SQLException {
        String dbPath = tempDir.resolve("sale-id-propagation-test.db").toString();

        DatabaseManager dbManager = DatabaseManager.createFromFile(dbPath);
        ProductRepository productRepository = new ProductRepository(dbManager);
        StockMovementRepository stockMovementRepository = new StockMovementRepository(dbManager);
        InventoryService inventoryService = new InventoryService(stockMovementRepository, productRepository);
        SaleRepository saleRepository = new SaleRepository(dbManager);
        ReceiptService receiptService = new ReceiptService(productRepository);
        SalesService salesService = new SalesService(saleRepository, stockMovementRepository, productRepository,
                inventoryService, receiptService, dbManager);

        Product product = new Product();
        product.setName("Producto ID Test");
        product.setCategoryName("Gaseosas");
        product.setPresentation("Botella");
        product.setCostPrice(100.0);
        product.setSalePrice(200.0);
        Long productId = productRepository.save(product);

        StockMovement entry = new StockMovement();
        entry.setProductId(productId);
        entry.setMovementType("ENTRY");
        entry.setQuantity(20);
        entry.setReferenceType("ADJUSTMENT");
        entry.setNotes("Carga inicial");
        stockMovementRepository.insert(entry);
        productRepository.updateStock(dbManager.getConnection(), productId, 20);

        Sale sale = new Sale();
        sale.setChannel("IN");
        sale.setPaymentMethod("CASH");
        SaleItem item = new SaleItem();
        item.setProductId(productId);
        item.setQuantity(5);
        item.setUnitPrice(200.0);

        boolean created = salesService.createSale(sale, List.of(item));

        assertThat(created).isTrue();
        // Fixed: the caller's Sale object now has the real generated id.
        assertThat(sale.getId()).isNotNull();

        assertThat(saleRepository.findAllActive()).hasSize(1);
        assertThat(saleRepository.findAllActive().get(0).getId()).isEqualTo(sale.getId());

        dbManager.close();
    }
}
