package com.softwaredebebidas;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Supplier;
import com.softwaredebebidas.repository.*;
import com.softwaredebebidas.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Load test that creates realistic data volumes.
 * Run manually with: mvn test -Dtest=LoadTest -Dload=true
 */
@EnabledIfSystemProperty(named = "load", matches = "true")
class LoadTest {

    private DatabaseManager dbManager;
    private ProductRepository productRepo;
    private SaleRepository saleRepo;
    private SalesService salesService;
    private InventoryService inventoryService;
    private StockMovementRepository stockRepo;
    private SupplierRepository supplierRepo;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
        productRepo = new ProductRepository(dbManager);
        saleRepo = new SaleRepository(dbManager);
        stockRepo = new StockMovementRepository(dbManager);
        supplierRepo = new SupplierRepository(dbManager);
        inventoryService = new InventoryService(stockRepo, productRepo);
        salesService = new SalesService(
                saleRepo, stockRepo, productRepo, inventoryService,
                new ReceiptService(productRepo), dbManager
        );
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) dbManager.close();
    }

    @Test
    void createManyProducts() throws SQLException {
        int count = 5000;
        Supplier supplier = createSupplier();

        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            Product p = new Product();
            p.setName("Producto de prueba #" + i);
            p.setCategory("Categoría " + (i % 50));
            p.setPresentation("Presentación");
            p.setCostPrice(100 + (i % 900));
            p.setSalePrice(200 + (i % 800));
            p.setMinStock(10);
            p.setSupplierId(supplier.getId());
            p.setBarcode("779" + String.format("%07d", i));
            productRepo.save(p);
        }
        long elapsed = System.currentTimeMillis() - start;

        assertThat(productRepo.findAllActive()).hasSize(count);
        System.out.println("Created " + count + " products in " + elapsed + "ms (" + (elapsed / count) + "ms per product)");
    }

    @Test
    void searchProductsPerformance() throws SQLException {
        Supplier supplier = createSupplier();

        for (int i = 0; i < 1000; i++) {
            Product p = new Product();
            p.setName("Bebida " + (i % 200) + " - Lote " + i);
            p.setCategory("Cat " + (i % 20));
            p.setPresentation("Botella");
            p.setCostPrice(100.0);
            p.setSalePrice(200.0);
            p.setMinStock(5);
            p.setSupplierId(supplier.getId());
            p.setBarcode("BAR" + i);
            productRepo.save(p);
        }

        long start = System.currentTimeMillis();
        int searches = 100;
        for (int i = 0; i < searches; i++) {
            productRepo.searchByName("bebida");
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Searched " + searches + " times in " + elapsed + "ms (avg " + (elapsed / searches) + "ms)");
    }

    @Test
    void createManySales() throws SQLException {
        Supplier supplier = createSupplier();

        Product p = new Product();
        p.setName("Producto base");
        p.setCategory("Cat");
        p.setPresentation("Botella");
        p.setCostPrice(100.0);
        p.setSalePrice(200.0);
        p.setMinStock(5);
        p.setSupplierId(supplier.getId());
        p.setBarcode("BASE001");
        productRepo.save(p);
        assertThat(p.getId()).isNotNull();

        com.softwaredebebidas.model.StockMovement entry = new com.softwaredebebidas.model.StockMovement();
        entry.setProductId(p.getId());
        entry.setMovementType("ENTRY");
        entry.setQuantity(10000);
        entry.setReferenceType("PURCHASE");
        stockRepo.insert(entry);

        int count = 1000;
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            com.softwaredebebidas.model.Sale sale = new com.softwaredebebidas.model.Sale();
            sale.setSaleDate(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            sale.setChannel(i % 2 == 0 ? "IN" : "PEDIDOSYA");
            sale.setPaymentMethod(i % 3 == 0 ? "CASH" : i % 3 == 1 ? "DEBIT_CARD" : "TRANSFER");

            com.softwaredebebidas.model.SaleItem item = new com.softwaredebebidas.model.SaleItem();
            item.setProductId(p.getId());
            item.setQuantity(1 + (i % 5));
            item.setUnitPrice(200.0);
            saleRepo.saveWithItems(sale, java.util.Collections.singletonList(item));
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Created " + count + " sales in " + elapsed + "ms (avg " + (elapsed / count) + "ms per sale)");
    }

    private Supplier createSupplier() throws SQLException {
        Supplier s = new Supplier();
        s.setName("Proveedor de prueba");
        Long id = supplierRepo.save(s);
        s.setId(id);
        return s;
    }
}
