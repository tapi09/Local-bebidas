package com.cocolatan.presenter;

import com.cocolatan.model.Product;
import com.cocolatan.repository.CategoryRepository;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.SubcategoryRepository;
import com.cocolatan.repository.SupplierRepository;
import com.cocolatan.service.InventoryService;
import com.cocolatan.util.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the zero-price dual-confirmation guard in
 * ProductPresenter (REQ-ZERO-PRICE-01 / REQ-ZERO-PRICE-02).
 *
 * <p>The two dialogs carry the technical warning texts mandated by the spec:
 * dialog 1 warns about lost revenue, dialog 2 about selling at no cost. A
 * cancel on either dialog aborts the operation before persistence.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductPresenterZeroPriceTest {

    /** Spec-mandated text of the first confirmation dialog (revenue warning). */
    private static final String FIRST_DIALOG_TEXT =
            "Sale price is zero — sales will generate no revenue. Are you sure?";
    /** Spec-mandated text of the second confirmation dialog (cost warning). */
    private static final String SECOND_DIALOG_TEXT =
            "This product will be sold at no cost. Confirm zero price?";

    @Mock
    private ProductRepository productRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SubcategoryRepository subcategoryRepository;

    private ProductPresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new ProductPresenter(productRepository, supplierRepository, inventoryService,
                categoryRepository, subcategoryRepository);
    }

    // ──────────────────────────────────────────────
    // saveProduct — zero price, dialog sequence
    // ──────────────────────────────────────────────

    @Test
    void saveZeroPriceBothConfirmedPersistsAndShowsBothDialogsInOrder() throws SQLException {
        Product product = zeroPriceProduct();
        when(productRepository.save(any(Product.class))).thenReturn(1L);
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
        verify(productRepository).save(product);
        // Both dialogs shown sequentially with the exact spec texts.
        assertThat(shownDialogs).containsExactly(FIRST_DIALOG_TEXT, SECOND_DIALOG_TEXT);
    }

    @Test
    void saveZeroPriceFirstDialogCancelledDoesNotPersistAndShowsOnlyFirstDialog() throws SQLException {
        Product product = zeroPriceProduct();
        List<String> shownDialogs = new ArrayList<>();

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            alerts.when(() -> AlertService.showConfirmDialog(anyString(), anyString()))
                    .thenAnswer(inv -> {
                        shownDialogs.add(inv.getArgument(1));
                        return false;
                    });
            result = presenter.saveProduct(product);
        }

        assertThat(result).isFalse();
        verify(productRepository, never()).save(any(Product.class));
        assertThat(shownDialogs).containsExactly(FIRST_DIALOG_TEXT);
    }

    @Test
    void saveZeroPriceSecondDialogCancelledDoesNotPersist() throws SQLException {
        Product product = zeroPriceProduct();
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
        verify(productRepository, never()).save(any(Product.class));
        assertThat(shownDialogs).containsExactly(FIRST_DIALOG_TEXT, SECOND_DIALOG_TEXT);
    }

    @Test
    void savePositivePriceShowsNoDialogsAndPersists() throws SQLException {
        Product product = positivePriceProduct();
        when(productRepository.save(any(Product.class))).thenReturn(1L);

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            result = presenter.saveProduct(product);
            alerts.verifyNoInteractions();
        }

        assertThat(result).isTrue();
        verify(productRepository).save(product);
    }

    // ──────────────────────────────────────────────
    // updateProduct — zero price, dialog sequence
    // ──────────────────────────────────────────────

    @Test
    void updateToZeroPriceBothConfirmedUpdatesAndShowsBothDialogsInOrder() throws SQLException {
        Product product = zeroPriceProduct();
        product.setId(7L);
        List<String> shownDialogs = new ArrayList<>();

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            alerts.when(() -> AlertService.showConfirmDialog(anyString(), anyString()))
                    .thenAnswer(inv -> {
                        shownDialogs.add(inv.getArgument(1));
                        return true;
                    });
            result = presenter.updateProduct(product);
        }

        assertThat(result).isTrue();
        verify(productRepository).update(product);
        assertThat(shownDialogs).containsExactly(FIRST_DIALOG_TEXT, SECOND_DIALOG_TEXT);
    }

    @Test
    void updateToZeroPriceFirstDialogCancelledDoesNotUpdate() throws SQLException {
        Product product = zeroPriceProduct();
        product.setId(7L);

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            alerts.when(() -> AlertService.showConfirmDialog(anyString(), anyString())).thenReturn(false);
            result = presenter.updateProduct(product);
        }

        assertThat(result).isFalse();
        verify(productRepository, never()).update(any(Product.class));
    }

    @Test
    void updateToZeroPriceSecondDialogCancelledDoesNotUpdate() throws SQLException {
        Product product = zeroPriceProduct();
        product.setId(7L);
        List<String> shownDialogs = new ArrayList<>();

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            alerts.when(() -> AlertService.showConfirmDialog(anyString(), anyString()))
                    .thenAnswer(inv -> {
                        shownDialogs.add(inv.getArgument(1));
                        return shownDialogs.size() == 1; // confirm first, cancel second
                    });
            result = presenter.updateProduct(product);
        }

        assertThat(result).isFalse();
        verify(productRepository, never()).update(any(Product.class));
        assertThat(shownDialogs).containsExactly(FIRST_DIALOG_TEXT, SECOND_DIALOG_TEXT);
    }

    @Test
    void updatePositivePriceShowsNoDialogsAndUpdates() throws SQLException {
        Product product = positivePriceProduct();
        product.setId(7L);

        boolean result;
        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            result = presenter.updateProduct(product);
            alerts.verifyNoInteractions();
        }

        assertThat(result).isTrue();
        verify(productRepository).update(product);
    }

    // ──────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────

    private Product zeroPriceProduct() {
        Product product = new Product();
        product.setName("Coca-Cola 500ml");
        product.setCategory("Gaseosa");
        product.setPresentation("Botella 500ml");
        product.setCostPrice(0.0);
        product.setSalePrice(0.0);
        product.setActive(true);
        return product;
    }

    private Product positivePriceProduct() {
        Product product = new Product();
        product.setName("Coca-Cola 500ml");
        product.setCategory("Gaseosa");
        product.setPresentation("Botella 500ml");
        product.setCostPrice(350.0);
        product.setSalePrice(600.0);
        product.setActive(true);
        return product;
    }
}
