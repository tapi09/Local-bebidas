package com.cocolatan.integration;

import com.cocolatan.model.Product;
import com.cocolatan.model.Supplier;
import com.cocolatan.presenter.ProductPresenter;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.StockMovementRepository;
import com.cocolatan.repository.SubcategoryRepository;
import com.cocolatan.repository.SupplierRepository;
import com.cocolatan.service.InventoryService;
import com.cocolatan.util.AlertService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for the zero-price dual-confirmation guard
 * (REQ-ZERO-PRICE-01 / REQ-ZERO-PRICE-02).
 *
 * <p>Deviation note: the design proposed TestFX UI automation, but this repo
 * has no TestFX harness (see {@code BackupExportIntegrationTest}); the flow is
 * exercised with real components (real SQLite DB via {@link DatabaseManager},
 * real {@link ProductRepository}) and only the native confirmation dialogs
 * mocked via {@code mockStatic(AlertService.class)} — matching the repository's
 * existing integration style.</p>
 */
class ProductZeroPriceIntegrationTest {

    private static final String FIRST_DIALOG_TEXT =
            "Sale price is zero — sales will generate no revenue. Are you sure?";
    private static final String SECOND_DIALOG_TEXT =
            "This product will be sold at no cost. Confirm zero price?";

    private DatabaseManager dbManager;
    private ProductRepository productRepository;
    private ProductPresenter presenter;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = DatabaseManager.createInMemory();
        productRepository = new ProductRepository(dbManager);
        SupplierRepository supplierRepository = new SupplierRepository(dbManager);
        InventoryService inventoryService =
                new InventoryService(new StockMovementRepository(dbManager), productRepository);
        presenter = new ProductPresenter(productRepository, supplierRepository, inventoryService);

        Supplier supplier = new Supplier();
        supplier.setName("Distribuidora Norte");
        supplierRepository.save(supplier);

        Product seeded = new Product();
        seeded.setName("Coca-Cola 500ml");
        seeded.setCategory("Gaseosas");
        seeded.setPresentation("Botella 500ml");
        seeded.setCostPrice(350.0);
        seeded.setSalePrice(500.0);
        seeded.setSupplierId(1L);
        seeded.setBarcode("7790001001");
        seeded.setMinStock(5);
        seeded.setActive(true);
        productRepository.save(seeded);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    // ──────────────────────────────────────────────
    // saveProduct — zero price, real persistence
    // ──────────────────────────────────────────────

    @Test
    void saveZeroPriceBothConfirmedPersistsProductWithZeroPrice() {
        Product product = newZeroPriceProduct("Quilmes 1L");
        List<String> shownDialogs = new ArrayList<>();

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            alerts.when(() -> AlertService.showConfirmDialog(anyString(), anyString()))
                    .thenAnswer(inv -> {
                        shownDialogs.add(inv.getArgument(1));
                        return true;
                    });
            result = presenter.saveProduct(product);
        }

        assertThat(result).isTrue();
        assertThat(shownDialogs).containsExactly(FIRST_DIALOG_TEXT, SECOND_DIALOG_TEXT);
        Optional<Product> saved = findByName("Quilmes 1L");
        assertThat(saved).isPresent();
        assertThat(saved.get().getSalePrice()).isEqualTo(0.0);
    }

    @Test
    void saveZeroPriceFirstDialogCancelledDoesNotPersist() {
        Product product = newZeroPriceProduct("Quilmes 1L");

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            alerts.when(() -> AlertService.showConfirmDialog(anyString(), anyString())).thenReturn(false);
            result = presenter.saveProduct(product);
        }

        assertThat(result).isFalse();
        assertThat(findByName("Quilmes 1L")).isEmpty();
    }

    @Test
    void saveZeroPriceSecondDialogCancelledDoesNotPersist() {
        Product product = newZeroPriceProduct("Quilmes 1L");
        List<String> shownDialogs = new ArrayList<>();

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            alerts.when(() -> AlertService.showConfirmDialog(anyString(), anyString()))
                    .thenAnswer(inv -> {
                        shownDialogs.add(inv.getArgument(1));
                        return shownDialogs.size() == 1; // confirm first, cancel second
                    });
            result = presenter.saveProduct(product);
        }

        assertThat(result).isFalse();
        assertThat(shownDialogs).containsExactly(FIRST_DIALOG_TEXT, SECOND_DIALOG_TEXT);
        assertThat(findByName("Quilmes 1L")).isEmpty();
    }

    @Test
    void savePositivePricePersistsWithoutDialogs() {
        Product product = newPositivePriceProduct("Quilmes 1L", 700.0);

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            result = presenter.saveProduct(product);
            alerts.verifyNoInteractions();
        }

        assertThat(result).isTrue();
        Optional<Product> saved = findByName("Quilmes 1L");
        assertThat(saved).isPresent();
        assertThat(saved.get().getSalePrice()).isEqualTo(700.0);
    }

    // ──────────────────────────────────────────────
    // updateProduct — update to zero price, real persistence
    // ──────────────────────────────────────────────

    @Test
    void updateExistingProductToZeroPriceBothConfirmedUpdates() {
        Product existing = findByName("Coca-Cola 500ml").orElseThrow();
        existing.setSalePrice(0.0);
        existing.setCostPrice(0.0);

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            alerts.when(() -> AlertService.showConfirmDialog(anyString(), anyString())).thenReturn(true);
            result = presenter.updateProduct(existing);
        }

        assertThat(result).isTrue();
        Optional<Product> updated = findByName("Coca-Cola 500ml");
        assertThat(updated).isPresent();
        assertThat(updated.get().getSalePrice()).isEqualTo(0.0);
    }

    @Test
    void updateExistingProductToZeroPriceFirstDialogCancelledKeepsOldPrice() {
        Product existing = findByName("Coca-Cola 500ml").orElseThrow();
        existing.setSalePrice(0.0);
        existing.setCostPrice(0.0);

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            alerts.when(() -> AlertService.showConfirmDialog(anyString(), anyString())).thenReturn(false);
            result = presenter.updateProduct(existing);
        }

        assertThat(result).isFalse();
        Optional<Product> unchanged = findByName("Coca-Cola 500ml");
        assertThat(unchanged).isPresent();
        assertThat(unchanged.get().getSalePrice()).isEqualTo(500.0);
    }

    @Test
    void updateExistingProductToZeroPriceSecondDialogCancelledKeepsOldPrice() {
        Product existing = findByName("Coca-Cola 500ml").orElseThrow();
        existing.setSalePrice(0.0);
        existing.setCostPrice(0.0);
        List<String> shownDialogs = new ArrayList<>();

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            alerts.when(() -> AlertService.showConfirmDialog(anyString(), anyString()))
                    .thenAnswer(inv -> {
                        shownDialogs.add(inv.getArgument(1));
                        return shownDialogs.size() == 1; // confirm first, cancel second
                    });
            result = presenter.updateProduct(existing);
        }

        assertThat(result).isFalse();
        assertThat(shownDialogs).containsExactly(FIRST_DIALOG_TEXT, SECOND_DIALOG_TEXT);
        Optional<Product> unchanged = findByName("Coca-Cola 500ml");
        assertThat(unchanged).isPresent();
        assertThat(unchanged.get().getSalePrice()).isEqualTo(500.0);
    }

    @Test
    void updateExistingProductToPositivePriceUpdatesWithoutDialogs() {
        Product existing = findByName("Coca-Cola 500ml").orElseThrow();
        existing.setSalePrice(750.0);

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            result = presenter.updateProduct(existing);
            alerts.verifyNoInteractions();
        }

        assertThat(result).isTrue();
        Optional<Product> updated = findByName("Coca-Cola 500ml");
        assertThat(updated).isPresent();
        assertThat(updated.get().getSalePrice()).isEqualTo(750.0);
    }

    // ──────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────

    private Product newZeroPriceProduct(String name) {
        Product product = new Product();
        product.setName(name);
        product.setCategory("Cervezas");
        product.setPresentation("Botella 1L");
        product.setCostPrice(0.0);
        product.setSalePrice(0.0);
        product.setActive(true);
        return product;
    }

    private Product newPositivePriceProduct(String name, double salePrice) {
        Product product = new Product();
        product.setName(name);
        product.setCategory("Cervezas");
        product.setPresentation("Botella 1L");
        product.setCostPrice(salePrice / 2);
        product.setSalePrice(salePrice);
        product.setActive(true);
        return product;
    }

    private Optional<Product> findByName(String name) {
        try {
            return productRepository.findAllActive().stream()
                    .filter(p -> name.equals(p.getName()))
                    .findFirst();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
