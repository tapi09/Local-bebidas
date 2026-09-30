package com.softwaredebebidas.view;

import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.presenter.SalePresenter;
import com.softwaredebebidas.service.SalesService;
import com.softwaredebebidas.util.AlertService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the display-only discount line of the cart and the Enter-key feedback
 * of the discount field.
 */
class SaleControllerDiscountLineTest {

    @BeforeAll
    static void initJavaFxToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // Toolkit already running from a previous test class in the same JVM.
        }
    }

    // ── pure helpers ──────────────────────────────

    @Test
    void discountLineLabelForPercentageDropsTrailingZeros() {
        assertThat(SaleController.discountLineLabel("PERCENTAGE", 20.0)).isEqualTo("Descuento 20%");
    }

    @Test
    void discountLineLabelForPercentageKeepsDecimalsWithComma() {
        assertThat(SaleController.discountLineLabel("PERCENTAGE", 12.5)).isEqualTo("Descuento 12,5%");
    }

    @Test
    void discountLineLabelForFixed() {
        assertThat(SaleController.discountLineLabel("FIXED", 400.0)).isEqualTo("Descuento fijo");
    }

    @Test
    void discountAppliedMessageForPercentage() {
        assertThat(SaleController.discountAppliedMessage("PERCENTAGE", 20.0, 400.0))
                .isEqualTo("Se aplicó un descuento de 20% (-$400,00).");
    }

    @Test
    void discountAppliedMessageForFixed() {
        assertThat(SaleController.discountAppliedMessage("FIXED", 400.0, 400.0))
                .isEqualTo("Se aplicó un descuento fijo de -$400,00.");
    }

    @Test
    void cartRowsAppendNegativeDiscountLineWhenDiscountActive() {
        List<SaleItem> cart = List.of(item(1L, 2, 500.0));

        List<SaleItem> rows = SaleController.cartRowsWithDiscount(cart, "PERCENTAGE", 20.0, 200.0);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isSameAs(cart.get(0));
        assertThat(rows.get(1)).isInstanceOf(SaleController.DiscountCartLine.class);
        SaleController.DiscountCartLine line = (SaleController.DiscountCartLine) rows.get(1);
        assertThat(line.getLabel()).isEqualTo("Descuento 20%");
        assertThat(line.getSubtotal()).isEqualTo(-200.0);
        assertThat(cart).hasSize(1); // the presenter's list is never modified
    }

    @Test
    void cartRowsHaveNoDiscountLineWithoutActiveDiscount() {
        List<SaleItem> cart = List.of(item(1L, 2, 500.0));

        assertThat(SaleController.cartRowsWithDiscount(cart, "NONE", 0, 0)).containsExactlyElementsOf(cart);
        assertThat(SaleController.cartRowsWithDiscount(cart, null, 0, 0)).containsExactlyElementsOf(cart);
        assertThat(SaleController.cartRowsWithDiscount(cart, "FIXED", 50.0, 0)).containsExactlyElementsOf(cart);
    }

    // ── controller wiring ──────────────────────────────

    @Test
    void enterWithValidDiscountShowsInfoDialog() throws Exception {
        Fixture f = new Fixture();
        f.field.setText("20");
        f.combo.getSelectionModel().select(1);
        when(f.presenter.getSaleDiscountType()).thenReturn("PERCENTAGE");
        when(f.presenter.getSaleDiscount()).thenReturn(20.0);
        when(f.presenter.getCartTotal()).thenReturn(2000.0);
        when(f.presenter.getDiscountedTotal()).thenReturn(1600.0);

        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            invoke(f.controller, "onDiscountEntered");

            alerts.verify(() -> AlertService.showInfoDialog("Descuento Aplicado",
                    "Se aplicó un descuento de 20% (-$400,00)."));
            alerts.verify(() -> AlertService.showWarningDialog(anyString(), anyString()), never());
        }
    }

    @Test
    void enterWithValueButNoTypeShowsWarning() throws Exception {
        Fixture f = new Fixture();
        f.field.setText("15");

        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            invoke(f.controller, "onDiscountEntered");

            alerts.verify(() -> AlertService.showWarningDialog("Tipo de Descuento",
                    "Elegí si el descuento es porcentual (%) o un monto fijo ($)."));
            alerts.verify(() -> AlertService.showInfoDialog(anyString(), anyString()), never());
        }
    }

    @Test
    void enterWithInvalidDiscountShowsOnlyTheErrorDialog() throws Exception {
        Fixture f = new Fixture();
        f.field.setText("150");
        f.combo.getSelectionModel().select(1);
        when(f.presenter.getDiscountedTotal())
                .thenThrow(new SalesService.ValidationException("El descuento porcentual no puede superar el 100%"));

        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            invoke(f.controller, "onDiscountEntered");

            alerts.verify(() -> AlertService.showErrorDialog(eq("Descuento Inválido"), anyString()));
            alerts.verify(() -> AlertService.showInfoDialog(anyString(), anyString()), never());
            alerts.verify(() -> AlertService.showWarningDialog(anyString(), anyString()), never());
        }
    }

    @Test
    void enterWithZeroValueShowsNoDialog() throws Exception {
        Fixture f = new Fixture();

        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            invoke(f.controller, "onDiscountEntered");

            alerts.verifyNoInteractions();
        }
    }

    @Test
    void removingTheDiscountLineClearsTheDiscountWithoutTouchingTheCart() throws Exception {
        Fixture f = new Fixture();
        f.combo.getSelectionModel().select(1);
        f.field.setText("20");
        f.amountLabel.setVisible(true);
        when(f.presenter.getCartTotal()).thenReturn(2000.0);
        when(f.presenter.getDiscountedTotal()).thenReturn(2000.0);
        TableView<SaleItem> table = new TableView<>();
        SaleController.DiscountCartLine line = new SaleController.DiscountCartLine("Descuento 20%", 400.0);
        table.setItems(FXCollections.observableArrayList(line));
        table.getSelectionModel().select(line);
        set(f.controller, "cartTable", table);

        invoke(f.controller, "onRemoveFromCart");

        verify(f.presenter).setSaleDiscount(0, "NONE");
        verify(f.presenter, never()).removeFromCart(anyLong());
        assertThat(f.combo.getValue()).isEqualTo("Sin descuento");
        assertThat(f.field.getText()).isEqualTo("0");
        assertThat(f.amountLabel.isVisible()).isFalse();
        assertThat(f.total.getText()).isEqualTo("$2.000,00");
    }

    // ── helpers ──────────────────────────────

    private static SaleItem item(Long productId, int quantity, double unitPrice) {
        SaleItem item = new SaleItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setSubtotal(quantity * unitPrice);
        return item;
    }

    /** Controller with only the discount-related controls injected, as the other view tests do. */
    private static final class Fixture {
        final SaleController controller = new SaleController();
        final SalePresenter presenter = mock(SalePresenter.class);
        final ComboBox<String> combo = new ComboBox<>();
        final TextField field = new TextField("0");
        final Label amountLabel = new Label();
        final Label total = new Label();

        Fixture() throws Exception {
            combo.setItems(FXCollections.observableArrayList("Sin descuento", "% Descuento", "Fijo $"));
            combo.getSelectionModel().selectFirst();
            set(controller, "presenter", presenter);
            set(controller, "discountCombo", combo);
            set(controller, "discountField", field);
            set(controller, "discountAmountLabel", amountLabel);
            set(controller, "totalLabel", total);
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void invoke(Object target, String methodName) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName);
        m.setAccessible(true);
        try {
            m.invoke(target);
        } catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw wrapped;
        }
    }
}
