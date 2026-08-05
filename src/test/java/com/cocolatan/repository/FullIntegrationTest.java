package com.cocolatan.repository;

import com.cocolatan.model.*;
import com.cocolatan.service.InventoryService;
import com.cocolatan.service.ReceiptService;
import com.cocolatan.service.ReportService;
import com.cocolatan.service.SalesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full end-to-end integration test: product → purchase → stock → sale → report.
 * Uses in-memory SQLite for real DB operations.
 */
class FullIntegrationTest {

    private DatabaseManager dbManager;
    private ProductRepository productRepository;
    private SupplierRepository supplierRepository;
    private PurchaseRepository purchaseRepository;
    private SaleRepository saleRepository;
    private StockMovementRepository stockMovementRepository;
    private InventoryService inventoryService;
    private SalesService salesService;
    private ReportService reportService;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = DatabaseManager.createInMemory();
        productRepository = new ProductRepository(dbManager);
        supplierRepository = new SupplierRepository(dbManager);
        purchaseRepository = new PurchaseRepository(dbManager);
        saleRepository = new SaleRepository(dbManager);
        stockMovementRepository = new StockMovementRepository(dbManager);
        inventoryService = new InventoryService(stockMovementRepository, productRepository);
        ReceiptService receiptService = new ReceiptService(productRepository);
        salesService = new SalesService(saleRepository, stockMovementRepository, productRepository, inventoryService, receiptService, dbManager);
        reportService = new ReportService(productRepository, saleRepository, stockMovementRepository, dbManager);
    }

    @Test
    void fullFlowCreateSupplierPurchaseStockSaleReport() throws SQLException {
        // 1. Create supplier
        Supplier supplier = new Supplier();
        supplier.setName("Distribuidora Belgrano");
        supplier.setContact("Juan Pérez");
        supplier.setPhone("11-4444-5555");
        supplier.setEmail("juan@belgrano.com");
        supplier.setAddress("Av. Belgrano 1234");
        Long supplierId = supplierRepository.save(supplier);
        assertThat(supplierId).isPositive();

        // 2. Create products
        Product coca = new Product();
        coca.setName("Coca-Cola 500ml");
        coca.setCategory("Gaseosas");
        coca.setPresentation("Botella 500ml");
        coca.setCostPrice(300.0);
        coca.setSalePrice(600.0);
        coca.setSupplierId(supplierId);
        coca.setBarcode("77900001");
        coca.setMinStock(10);
        Long cocaId = productRepository.save(coca);

        Product pepsi = new Product();
        pepsi.setName("Pepsi 500ml");
        pepsi.setCategory("Gaseosas");
        pepsi.setPresentation("Botella 500ml");
        pepsi.setCostPrice(280.0);
        pepsi.setSalePrice(550.0);
        pepsi.setSupplierId(supplierId);
        pepsi.setBarcode("77900002");
        pepsi.setMinStock(10);
        Long pepsiId = productRepository.save(pepsi);

        assertThat(cocaId).isPositive();
        assertThat(pepsiId).isPositive();

        // 3. Create purchase (stock ENTRY)
        Purchase purchase = new Purchase();
        purchase.setSupplierId(supplierId);
        purchase.setInvoiceRef("FC-001");
        purchase.setPurchaseDate("01/07/2026");
        purchase.setTotalAmount(0);

        PurchaseItem item1 = new PurchaseItem();
        item1.setProductId(cocaId);
        item1.setQuantity(100);
        item1.setUnitCost(300.0);
        item1.setLotNumber("LOT-001");

        PurchaseItem item2 = new PurchaseItem();
        item2.setProductId(pepsiId);
        item2.setQuantity(50);
        item2.setUnitCost(280.0);
        item2.setLotNumber("LOT-002");

        Long purchaseId = purchaseRepository.saveWithItems(purchase, Arrays.asList(item1, item2));
        assertThat(purchaseId).isPositive();

        // Create ENTRY stock movements (simulating what PurchasePresenter does)
        StockMovement entry1 = new StockMovement();
        entry1.setProductId(cocaId);
        entry1.setMovementType("ENTRY");
        entry1.setQuantity(100);
        entry1.setReferenceType("PURCHASE");
        entry1.setReferenceId(purchaseId);
        entry1.setNotes("Compra #1");
        stockMovementRepository.insert(entry1);

        StockMovement entry2 = new StockMovement();
        entry2.setProductId(pepsiId);
        entry2.setMovementType("ENTRY");
        entry2.setQuantity(50);
        entry2.setReferenceType("PURCHASE");
        entry2.setReferenceId(purchaseId);
        entry2.setNotes("Compra #1");
        stockMovementRepository.insert(entry2);

        // 4. Verify stock after purchase
        int cocaStock = inventoryService.getCurrentStock(cocaId);
        int pepsiStock = inventoryService.getCurrentStock(pepsiId);
        assertThat(cocaStock).isEqualTo(100);
        assertThat(pepsiStock).isEqualTo(50);

        // 5. Create sale (stock EXIT)
        Sale sale = new Sale();
        sale.setSaleDate("15/07/2026");
        sale.setChannel("IN");
        sale.setPaymentMethod("CASH");

        SaleItem saleItem1 = new SaleItem();
        saleItem1.setProductId(cocaId);
        saleItem1.setQuantity(30);
        saleItem1.setUnitPrice(600.0);

        SaleItem saleItem2 = new SaleItem();
        saleItem2.setProductId(pepsiId);
        saleItem2.setQuantity(20);
        saleItem2.setUnitPrice(550.0);

        boolean saleCreated = salesService.createSale(sale, Arrays.asList(saleItem1, saleItem2));
        assertThat(saleCreated).isTrue();

        // 6. Verify stock after sale
        int cocaStockAfter = inventoryService.getCurrentStock(cocaId);
        int pepsiStockAfter = inventoryService.getCurrentStock(pepsiId);
        assertThat(cocaStockAfter).isEqualTo(70);  // 100 - 30
        assertThat(pepsiStockAfter).isEqualTo(30);   // 50 - 20

        // 7. Verify reports
        // Margin report
        List<ReportService.MarginReport> margins = reportService.getMarginReport();
        assertThat(margins).hasSize(2);
        ReportService.MarginReport cocaMargin = margins.stream()
                .filter(m -> m.getProductName().equals("Coca-Cola 500ml")).findFirst().orElse(null);
        assertThat(cocaMargin).isNotNull();
        assertThat(cocaMargin.getMarginPercent()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.01));

        // Sales by period report
        List<ReportService.SalesPeriodReport> salesReport = reportService.getSalesByPeriodReport("01/07/2026", "31/07/2026");
        assertThat(salesReport).hasSize(1);
        assertThat(salesReport.get(0).getTotalRevenue()).isCloseTo(29000.0, org.assertj.core.data.Offset.offset(0.01));
        // 30*600 + 20*550 = 18000 + 11000 = 29000

        // Channel comparison report
        List<ReportService.ChannelReport> channels = reportService.getChannelComparisonReport("01/07/2026", "31/07/2026");
        assertThat(channels).hasSize(1);
        assertThat(channels.get(0).getChannel()).isEqualTo("IN");
        assertThat(channels.get(0).getPercentage()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.01));

        // Stock value report
        List<ReportService.StockValueReport> stockValues = reportService.getStockValueReport();
        assertThat(stockValues).hasSize(2);
        double totalValue = reportService.getStockValueReportTotal();
        // (70 * 300) + (30 * 280) = 21000 + 8400 = 29400
        assertThat(totalValue).isCloseTo(29400.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void multiChannelSaleChannelComparison() throws SQLException {
        // Setup product
        Product product = new Product();
        product.setName("Agua 500ml");
        product.setCategory("Aguas");
        product.setPresentation("Botella 500ml");
        product.setCostPrice(100.0);
        product.setSalePrice(200.0);
        product.setBarcode("77900003");
        product.setMinStock(5);
        Long productId = productRepository.save(product);

        // Add stock
        StockMovement entry = new StockMovement();
        entry.setProductId(productId);
        entry.setMovementType("ENTRY");
        entry.setQuantity(200);
        entry.setReferenceType("PURCHASE");
        entry.setNotes("Stock inicial");
        stockMovementRepository.insert(entry);

        // Sale LOCAL
        Sale localSale = new Sale();
        localSale.setSaleDate("01/07/2026");
        localSale.setChannel("IN");
        localSale.setPaymentMethod("CASH");
        SaleItem localItem = new SaleItem();
        localItem.setProductId(productId);
        localItem.setQuantity(10);
        localItem.setUnitPrice(200.0);
        salesService.createSale(localSale, Collections.singletonList(localItem));

        // Sale PEDIDOSYA
        Sale pySale = new Sale();
        pySale.setSaleDate("01/07/2026");
        pySale.setChannel("PEDIDOSYA");
        pySale.setPaymentMethod("CASH");
        SaleItem pyItem = new SaleItem();
        pyItem.setProductId(productId);
        pyItem.setQuantity(5);
        pyItem.setUnitPrice(200.0);
        salesService.createSale(pySale, Collections.singletonList(pyItem));

        // Channel comparison
        List<ReportService.ChannelReport> channels = reportService.getChannelComparisonReport("01/07/2026", "31/07/2026");
        assertThat(channels).hasSize(2);

        ReportService.ChannelReport local = channels.stream()
                .filter(c -> "IN".equals(c.getChannel())).findFirst().orElse(null);
        ReportService.ChannelReport py = channels.stream()
                .filter(c -> "PEDIDOSYA".equals(c.getChannel())).findFirst().orElse(null);

        assertThat(local).isNotNull();
        assertThat(local.getRevenue()).isCloseTo(2000.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(local.getPercentage()).isCloseTo(66.67, org.assertj.core.data.Offset.offset(0.5));

        assertThat(py).isNotNull();
        assertThat(py.getRevenue()).isCloseTo(1000.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(py.getPercentage()).isCloseTo(33.33, org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    void lowStockDetectionAfterSale() throws SQLException {
        Product product = new Product();
        product.setName("Energizante");
        product.setCategory("Energizantes");
        product.setPresentation("Botella 500ml");
        product.setCostPrice(400.0);
        product.setSalePrice(700.0);
        product.setBarcode("77900004");
        product.setMinStock(10);
        Long productId = productRepository.save(product);

        // Add 15 units
        StockMovement entry = new StockMovement();
        entry.setProductId(productId);
        entry.setMovementType("ENTRY");
        entry.setQuantity(15);
        entry.setReferenceType("PURCHASE");
        entry.setNotes("Stock");
        stockMovementRepository.insert(entry);

        // Sell 8 → remaining 7 < minStock 10
        Sale sale = new Sale();
        sale.setSaleDate("01/07/2026");
        sale.setChannel("IN");
        sale.setPaymentMethod("CASH");
        SaleItem item = new SaleItem();
        item.setProductId(productId);
        item.setQuantity(8);
        item.setUnitPrice(700.0);
        salesService.createSale(sale, Collections.singletonList(item));

        // Verify low stock
        String status = inventoryService.getStockStatus(productId);
        assertThat(status).isEqualTo("LOW");

        int stock = inventoryService.getCurrentStock(productId);
        assertThat(stock).isEqualTo(7);
    }
}
