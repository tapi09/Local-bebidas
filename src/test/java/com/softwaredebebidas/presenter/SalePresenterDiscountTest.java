package com.softwaredebebidas.presenter;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.repository.CustomerRepository;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.service.InventoryService;
import com.softwaredebebidas.service.SalesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the discount clamp and range validation in
 * SalePresenter.getDiscountedTotal (REQ-DISC-01 / REQ-DISC-03 / REQ-DISC-04).
 *
 * <p>Spec-tension resolution: REQ-DISC-01 describes a 150% PERCENTAGE discount
 * being clamped to $0, while REQ-DISC-03 rejects any percentage outside [0,100]
 * with a ValidationException. Per the design decision ("presenter validates
 * early for UX"), validation wins: 150% throws instead of clamping. The
 * Math.max clamp remains as defense-in-depth and is observable at the valid
 * boundaries (100% → $0, FIXED == subtotal → $0).</p>
 */
@ExtendWith(MockitoExtension.class)
class SalePresenterDiscountTest {

    @Mock
    private SalesService salesService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CustomerRepository customerRepository;

    private SalePresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new SalePresenter(salesService, inventoryService, productRepository, customerRepository);
        when(inventoryService.validateStock(1L, 1)).thenReturn(true);
        Product product = new Product();
        product.setId(1L);
        product.setName("Coca-Cola 500ml");
        product.setSalePrice(100.0);
        presenter.addToCart(product, 1); // subtotal 100
    }

    // ──────────────────────────────────────────────
    // PERCENTAGE — clamp + range validation
    // ──────────────────────────────────────────────

    @Test
    void percentage50On100Returns50() {
        presenter.setSaleDiscount(50, "PERCENTAGE");

        assertThat(presenter.getDiscountedTotal()).isEqualTo(50.0);
    }

    @Test
    void percentage100On100FloorsToZero() {
        presenter.setSaleDiscount(100, "PERCENTAGE");

        assertThat(presenter.getDiscountedTotal()).isEqualTo(0.0);
    }

    @Test
    void percentageAbove100ThrowsValidationException() {
        presenter.setSaleDiscount(150, "PERCENTAGE");

        assertThatThrownBy(() -> presenter.getDiscountedTotal())
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("100");
    }

    @Test
    void percentageNegativeThrowsValidationException() {
        presenter.setSaleDiscount(-10, "PERCENTAGE");

        assertThatThrownBy(() -> presenter.getDiscountedTotal())
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    void percentageZeroIsValidAndLeavesTotalUnchanged() {
        presenter.setSaleDiscount(0, "PERCENTAGE");

        assertThat(presenter.getDiscountedTotal()).isEqualTo(100.0);
    }

    // ──────────────────────────────────────────────
    // FIXED — clamp + range validation
    // ──────────────────────────────────────────────

    @Test
    void fixed30On100Returns70() {
        presenter.setSaleDiscount(30, "FIXED");

        assertThat(presenter.getDiscountedTotal()).isEqualTo(70.0);
    }

    @Test
    void fixedEqualSubtotalFloorsToZero() {
        presenter.setSaleDiscount(100, "FIXED");

        assertThat(presenter.getDiscountedTotal()).isEqualTo(0.0);
    }

    @Test
    void fixedAboveSubtotalThrowsValidationException() {
        presenter.setSaleDiscount(150, "FIXED");

        assertThatThrownBy(() -> presenter.getDiscountedTotal())
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("subtotal");
    }

    @Test
    void fixedNegativeThrowsValidationException() {
        presenter.setSaleDiscount(-5, "FIXED");

        assertThatThrownBy(() -> presenter.getDiscountedTotal())
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("negativo");
    }

    // ──────────────────────────────────────────────
    // NONE / unknown types — unchanged
    // ──────────────────────────────────────────────

    @Test
    void noneTypeLeavesTotalUnchanged() {
        presenter.setSaleDiscount(50, "NONE");

        assertThat(presenter.getDiscountedTotal()).isEqualTo(100.0);
    }

    @Test
    void unknownTypeLeavesTotalUnchanged() {
        presenter.setSaleDiscount(50, "BOGUS");

        assertThat(presenter.getDiscountedTotal()).isEqualTo(100.0);
    }

    // ──────────────────────────────────────────────
    // Triangulation — multi-item cart
    // ──────────────────────────────────────────────

    @Test
    void percentage50OnMultiItemCartHalvesSubtotal() {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        presenter.updateCartQuantity(1L, 2); // subtotal 200
        presenter.setSaleDiscount(50, "PERCENTAGE");

        assertThat(presenter.getDiscountedTotal()).isEqualTo(100.0);
    }
}
