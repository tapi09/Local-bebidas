package com.cocolatan.repository;

import com.cocolatan.model.*;
import com.cocolatan.presenter.SalePresenter;
import com.cocolatan.service.InventoryService;
import com.cocolatan.service.ReceiptService;
import com.cocolatan.service.SalesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for the full POS flow:
 * search product → add to cart → select channel → payment → complete sale → verify stock decremented.
 */
class SaleIntegrationTest {

    private DatabaseManager dbManager;
    private SaleRepository saleRepository;
    private ProductRepository productRepository;
    private StockMovementRepository stockMovementRepository;
    private InventoryService inventoryService;
    private SalesService salesService;
    private SalePresenter presenter;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = DatabaseManager.createInMemory();
        saleRepository = new SaleRepository(dbManager);
        productRepository = new ProductRepository(dbManager);
        stockMovementRepository = new StockMovementRepository(dbManager);
        inventoryService = new InventoryService(stockMovementRepository, productRepository);
        ReceiptService receiptService = new ReceiptService(productRepository);
        salesService = new SalesService(saleRepository, stockMovementRepository, productRepository, inventoryService, receiptService, dbManager);
        CustomerRepository customerRepository = new CustomerRepository(dbManager);
        presenter = new SalePresenter(salesService, inventoryService, productRepository, customerRepository);

        // Seed data: supplier + products + initial stock
        SupplierRepository supplierRepository = new SupplierRepository(dbManager);
        Supplier supplier = new Supplier();
        supplier.setName("Distribuidora Norte");
        supplierRepository.save(supplier);

        Product coke = new Product();
        coke.setName("Coca-Cola 500ml");
        coke.setCategory("Gaseosas");
        coke.setPresentation("Botella 500ml");
        coke.setCostPrice(350.0);
        coke.setSalePrice(600.0);
        coke.setSupplierId(1L);
        coke.setBarcode("7790001001");
        coke.setMinStock(5);
        productRepository.save(coke);

        Product pepsi = new Product();
        pepsi.setName("Pepsi 500ml");
        pepsi.setCategory("Gaseosas");
        pepsi.setPresentation("Botella 500ml");
        pepsi.setCostPrice(300.0);
        pepsi.setSalePrice(550.0);
        pepsi.setSupplierId(1L);
        pepsi.setBarcode("7790002002");
        pepsi.setMinStock(5);
        productRepository.save(pepsi);

        // Add stock: ENTRY +24 for coke, +12 for pepsi
        StockMovement cokeEntry = new StockMovement();
        cokeEntry.setProductId(1L);
        cokeEntry.setMovementType("ENTRY");
        cokeEntry.setQuantity(24);
        cokeEntry.setReferenceType("PURCHASE");
        cokeEntry.setReferenceId(1L);
        stockMovementRepository.insert(cokeEntry);
        // Update denormalized current_stock
        productRepository.updateStock(dbManager.getConnection(), 1L, 24);

        StockMovement pepsiEntry = new StockMovement();
        pepsiEntry.setProductId(2L);
        pepsiEntry.setMovementType("ENTRY");
        pepsiEntry.setQuantity(12);
        pepsiEntry.setReferenceType("PURCHASE");
        pepsiEntry.setReferenceId(1L);
        stockMovementRepository.insert(pepsiEntry);
        // Update denormalized current_stock
        productRepository.updateStock(dbManager.getConnection(), 2L, 12);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void fullSaleFlowLocalCash() throws SQLException {
        // 1. Search product
        List<Product> results = presenter.searchProducts("Coca");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Coca-Cola 500ml");

        // 2. Add to cart
        boolean added = presenter.addToCart(results.get(0), 2);
        assertThat(added).isTrue();
        assertThat(presenter.getCartItems()).hasSize(1);

        // 3. Set channel and payment
        presenter.setChannel("IN");
        presenter.setPaymentMethod("CASH");

        // 4. Complete sale
        String receipt = presenter.completeSale();
        assertThat(receipt).isNotNull();
        assertThat(receipt).contains("Coca-Cola 500ml");
        assertThat(receipt).contains("1.200");

        // 5. Verify stock decremented: 24 - 2 = 22
        int stock = inventoryService.getCurrentStock(1L);
        assertThat(stock).isEqualTo(22);
    }

    @Test
    void fullSaleFlowPedidosYaDebit() throws SQLException {
        // 1. Search product
        List<Product> results = presenter.searchProducts("Pepsi");
        assertThat(results).hasSize(1);

        // 2. Add to cart
        presenter.addToCart(results.get(0), 3);

        // 3. Set channel and payment
        presenter.setChannel("PEDIDOSYA");
        presenter.setPaymentMethod("DEBIT_CARD");

        // 4. Complete sale
        String receipt = presenter.completeSale();
        assertThat(receipt).isNotNull();
        assertThat(receipt).contains("PedidosYa");
        assertThat(receipt).contains("Tarjeta de Débito");

        // 5. Verify stock decremented: 12 - 3 = 9
        int stock = inventoryService.getCurrentStock(2L);
        assertThat(stock).isEqualTo(9);
    }

    @Test
    void barcodeScanAddsProductToCart() throws SQLException {
        // 1. Scan barcode
        Product product = presenter.findByBarcode("7790001001");
        assertThat(product).isNotNull();
        assertThat(product.getName()).isEqualTo("Coca-Cola 500ml");

        // 2. Add to cart
        boolean added = presenter.addToCart(product, 1);
        assertThat(added).isTrue();

        // 3. Complete sale
        presenter.setChannel("IN");
        presenter.setPaymentMethod("CASH");
        String receipt = presenter.completeSale();
        assertThat(receipt).isNotNull();

        // 4. Verify stock: 24 - 1 = 23
        assertThat(inventoryService.getCurrentStock(1L)).isEqualTo(23);
    }

    @Test
    void multiProductSaleCreatesMultipleMovements() throws SQLException {
        // 1. Add two products
        Product coke = productRepository.findById(1L).orElseThrow();
        Product pepsi = productRepository.findById(2L).orElseThrow();

        presenter.addToCart(coke, 2);
        presenter.addToCart(pepsi, 1);

        // 2. Complete sale
        presenter.setChannel("IN");
        presenter.setPaymentMethod("CASH");
        presenter.completeSale();

        // 3. Verify both stocks decremented
        assertThat(inventoryService.getCurrentStock(1L)).isEqualTo(22);  // 24 - 2
        assertThat(inventoryService.getCurrentStock(2L)).isEqualTo(11);  // 12 - 1
    }

    @Test
    void saleCreatesStockMovementsWithCorrectReferences() throws SQLException {
        // 1. Add product and complete sale
        Product coke = productRepository.findById(1L).orElseThrow();
        presenter.addToCart(coke, 2);
        presenter.setChannel("IN");
        presenter.setPaymentMethod("CASH");
        presenter.completeSale();

        // 2. Verify stock movement exists with correct type
        List<StockMovement> movements = stockMovementRepository.findByProductId(1L);
        assertThat(movements).isNotEmpty();

        StockMovement exitMovement = movements.stream()
                .filter(m -> "EXIT".equals(m.getMovementType()))
                .findFirst()
                .orElse(null);
        assertThat(exitMovement).isNotNull();
        assertThat(exitMovement.getQuantity()).isEqualTo(2);
        assertThat(exitMovement.getReferenceType()).isEqualTo("SALE");
        assertThat(exitMovement.getReferenceId()).isNotNull();
    }

    @Test
    void insufficientStockBlocksSale() throws SQLException {
        // 1. Try to sell more than available
        Product coke = productRepository.findById(1L).orElseThrow();

        assertThatThrownBy(() -> presenter.addToCart(coke, 30))  // Only 24 in stock
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("Stock insuficiente");
        assertThat(presenter.getCartItems()).isEmpty();
    }

    @Test
    void cartTotalReflectsSalePriceNotCostPrice() throws SQLException {
        Product coke = productRepository.findById(1L).orElseThrow();
        presenter.addToCart(coke, 2);

        // Total should be 2 * 600 (sale price) = 1200, not 2 * 350 (cost price)
        assertThat(presenter.getCartTotal()).isEqualTo(1200.0);
    }
}
