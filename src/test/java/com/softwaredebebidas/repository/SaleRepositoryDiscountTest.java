package com.softwaredebebidas.repository;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.model.Supplier;
import com.softwaredebebidas.service.SalesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository-level tests for the discount clamp and range validation in
 * SaleRepository.applySaleDiscount (REQ-DISC-02 / REQ-DISC-03 / REQ-DISC-04),
 * exercised through the public saveWithItems entry point. The private
 * applySaleDiscount runs before the INSERT, so an invalid discount must reject
 * the whole sale without persisting anything.
 */
class SaleRepositoryDiscountTest {

    private DatabaseManager dbManager;
    private SaleRepository repository;
    private ProductRepository productRepository;
    private SupplierRepository supplierRepository;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = DatabaseManager.createInMemory();
        repository = new SaleRepository(dbManager);
        productRepository = new ProductRepository(dbManager);
        supplierRepository = new SupplierRepository(dbManager);

        Supplier supplier = new Supplier();
        supplier.setName("Distribuidora Norte");
        supplierRepository.save(supplier);

        Product product = new Product();
        product.setName("Coca-Cola 500ml");
        product.setCategory("Gaseosas");
        product.setPresentation("Botella 500ml");
        product.setCostPrice(350.0);
        product.setSalePrice(600.0);
        product.setSupplierId(1L);
        product.setBarcode("7790001001");
        product.setMinStock(5);
        productRepository.save(product);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    // ──────────────────────────────────────────────
    // PERCENTAGE — clamp + validation on persist
    // ──────────────────────────────────────────────

    @Test
    void percentage50On1200Persists600() throws SQLException {
        Long id = saveWithDiscount(50, "PERCENTAGE"); // 2 * 600 = 1200

        Optional<Sale> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getTotalAmount()).isEqualTo(600.0);
    }

    @Test
    void percentage100PersistsZero() throws SQLException {
        Long id = saveWithDiscount(100, "PERCENTAGE");

        Optional<Sale> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getTotalAmount()).isEqualTo(0.0);
    }

    @Test
    void percentageAbove100RejectedAndNothingPersisted() throws SQLException {
        assertThatThrownBy(() -> saveWithDiscount(150, "PERCENTAGE"))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("100");

        assertThat(repository.findHistory()).isEmpty();
    }

    @Test
    void percentageNegativeRejectedAndNothingPersisted() throws SQLException {
        assertThatThrownBy(() -> saveWithDiscount(-10, "PERCENTAGE"))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("negativo");

        assertThat(repository.findHistory()).isEmpty();
    }

    // ──────────────────────────────────────────────
    // FIXED — clamp + validation on persist
    // ──────────────────────────────────────────────

    @Test
    void fixed300On1200Persists900() throws SQLException {
        Long id = saveWithDiscount(300, "FIXED");

        Optional<Sale> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getTotalAmount()).isEqualTo(900.0);
    }

    @Test
    void fixedEqualSubtotalPersistsZero() throws SQLException {
        Long id = saveWithDiscount(1200, "FIXED");

        Optional<Sale> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getTotalAmount()).isEqualTo(0.0);
    }

    @Test
    void fixedAboveSubtotalRejectedAndNothingPersisted() throws SQLException {
        assertThatThrownBy(() -> saveWithDiscount(1500, "FIXED"))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("subtotal");

        assertThat(repository.findHistory()).isEmpty();
    }

    @Test
    void fixedNegativeRejectedAndNothingPersisted() throws SQLException {
        assertThatThrownBy(() -> saveWithDiscount(-5, "FIXED"))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("negativo");

        assertThat(repository.findHistory()).isEmpty();
    }

    // ──────────────────────────────────────────────
    // NONE / null type — unchanged
    // ──────────────────────────────────────────────

    @Test
    void noDiscountPersistsSubtotal() throws SQLException {
        Long id = saveWithDiscount(0, "NONE");

        Optional<Sale> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getTotalAmount()).isEqualTo(1200.0);
    }

    @Test
    void noneTypeIgnoresDiscountValueAndPersistsSubtotal() throws SQLException {
        Sale sale = createSale();
        sale.setDiscount(10.0);
        sale.setDiscountType("NONE");

        Long id = repository.saveWithItems(sale, Collections.singletonList(createSaleItem()));

        Optional<Sale> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getTotalAmount()).isEqualTo(1200.0);
    }

    // ──────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────

    private Long saveWithDiscount(double discount, String type) throws SQLException {
        Sale sale = createSale();
        sale.setDiscount(discount);
        sale.setDiscountType(type);
        return repository.saveWithItems(sale, Collections.singletonList(createSaleItem()));
    }

    private Sale createSale() {
        Sale sale = new Sale();
        sale.setSaleDate("22/07/2026");
        sale.setChannel("IN");
        sale.setPaymentMethod("CASH");
        return sale;
    }

    private SaleItem createSaleItem() {
        SaleItem item = new SaleItem();
        item.setProductId(1L);
        item.setQuantity(2);
        item.setUnitPrice(600.0); // 2 * 600 = 1200 subtotal
        return item;
    }
}
