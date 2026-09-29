package com.cocolatan.view;

import com.cocolatan.model.Product;
import com.cocolatan.presenter.SalePresenter;
import com.cocolatan.service.SalesService;
import com.cocolatan.util.AlertService;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class SaleControllerTest {

    @BeforeAll
    static void initJavaFxToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // Toolkit already running from a previous test class in the same JVM — fine.
        }
    }

    private Product product(String name, String category, String barcode) {
        Product p = new Product();
        p.setName(name);
        p.setCategory(category);
        p.setBarcode(barcode);
        return p;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private void invokePrivateVoid(Object target, String methodName) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName);
        m.setAccessible(true);
        try {
            m.invoke(target);
        } catch (InvocationTargetException wrapped) {
            // Unwrap so the test sees the real exception thrown by the handler body,
            // same as what would propagate to the FX event dispatcher in production.
            if (wrapped.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw wrapped;
        }
    }

    /**
     * REGRESSION for bug B3 (auditoria/informe_auditoria_v3_fase1.md): SalePresenter.addToCart()
     * now throws SalesService.ValidationException on insufficient stock instead of returning
     * false, but onAddToCart() only catches NumberFormatException. Expected/correct behavior:
     * the user sees a specific "Stock Insuficiente" dialog and the exception never escapes the
     * handler. Today this test fails because the ValidationException propagates uncaught.
     */
    @Test
    void onAddToCartWithInsufficientStockShowsSpecificDialogInsteadOfPropagating() throws Exception {
        Product selected = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        SaleController controller = new SaleController();
        SalePresenter presenter = mock(SalePresenter.class);
        when(presenter.addToCart(any(Product.class), anyInt(), anyString()))
                .thenThrow(new SalesService.ValidationException(
                        "Stock insuficiente para Coca-Cola 500ml. Disponible: 2, solicitado: 5"));
        setField(controller, "presenter", presenter);

        TableView<Product> productsTable = new TableView<>();
        productsTable.getItems().add(selected);
        productsTable.getSelectionModel().select(selected);
        setField(controller, "productsTable", productsTable);

        TextField quantityField = new TextField("5");
        setField(controller, "quantityField", quantityField);
        setField(controller, "channelCombo", new ComboBox<String>());

        try (MockedStatic<AlertService> alertMock = mockStatic(AlertService.class)) {
            invokePrivateVoid(controller, "onAddToCart");

            alertMock.verify(
                    () -> AlertService.showErrorDialog(
                            org.mockito.ArgumentMatchers.contains("Stock"),
                            org.mockito.ArgumentMatchers.contains("Coca-Cola")),
                    times(1));
        }
    }

    @Test
    void matchesQueryMatchesByCategory() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery("gaseosa", p)).isTrue();
    }

    @Test
    void matchesQueryMatchesByName() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery("coca", p)).isTrue();
    }

    @Test
    void matchesQueryMatchesByBarcode() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery("1234567", p)).isTrue();
    }

    @Test
    void matchesQueryIsCaseInsensitive() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery("GASEOSA", p)).isTrue();
        assertThat(SaleController.matchesQuery("COCA", p)).isTrue();
    }

    @Test
    void matchesQueryWithEmptyQueryReturnsTrue() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery("", p)).isTrue();
        assertThat(SaleController.matchesQuery("   ", p)).isTrue();
    }

    @Test
    void matchesQueryWithNullQueryReturnsTrue() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery(null, p)).isTrue();
    }

    @Test
    void matchesQueryNoMatchReturnsFalse() {
        Product p = product("Coca-Cola 500ml", "Gaseosa", "7790001234567");

        assertThat(SaleController.matchesQuery("vino", p)).isFalse();
    }

    @Test
    void matchesQueryWithNullCategoryDoesNotThrow() {
        Product p = product("Coca-Cola 500ml", null, null);

        assertThat(SaleController.matchesQuery("gaseosa", p)).isFalse();
        assertThat(SaleController.matchesQuery("coca", p)).isTrue();
    }

    @Test
    void matchesQueryMatchesBySubcategory() {
        Product p = product("Quilmes 1L", "Cervezas", "7790001234567");
        p.setSubcategoryName("Latas");

        assertThat(SaleController.matchesQuery("latas", p)).isTrue();
        assertThat(SaleController.matchesQuery("LATAS", p)).isTrue();
    }

    @Test
    void matchesQueryWithNullSubcategoryDoesNotThrow() {
        Product p = product("Quilmes 1L", "Cervezas", "7790001234567");

        assertThat(SaleController.matchesQuery("latas", p)).isFalse();
        assertThat(SaleController.matchesQuery("quilmes", p)).isTrue();
    }
}
