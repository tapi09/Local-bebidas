package com.softwaredebebidas.integration;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.model.StockMovement;
import com.softwaredebebidas.model.Supplier;
import com.softwaredebebidas.presenter.SalePresenter;
import com.softwaredebebidas.repository.CustomerRepository;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.SaleRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import com.softwaredebebidas.repository.SupplierRepository;
import com.softwaredebebidas.service.InventoryService;
import com.softwaredebebidas.service.ReceiptService;
import com.softwaredebebidas.service.SalesService;
import com.softwaredebebidas.util.AlertService;
import com.softwaredebebidas.util.CurrencyFormatter;
import com.softwaredebebidas.view.SaleController;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.collections.FXCollections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the discount clamp and validation
 * (REQ-DISC-01..04).
 *
 * <p>Deviation note: the design proposed TestFX UI automation, but this repo
 * has no TestFX harness (see {@code BackupExportIntegrationTest}); the POS
 * discount flow is exercised with real components (real SQLite DB, real
 * {@link SaleRepository}, real {@link SalesService}) and the controller's
 * discount-input wiring with a mocked presenter plus real JavaFX controls.</p>
 */
@ExtendWith(MockitoExtension.class)
class DiscountValidationIntegrationTest {

    private DatabaseManager dbManager;
    private SaleRepository saleRepository;
    private ProductRepository productRepository;
    private StockMovementRepository stockMovementRepository;
    private InventoryService inventoryService;
    private SalePresenter presenter;

    @Mock
    private SalePresenter mockPresenter;

    @BeforeAll
    static void initJavaFxToolkit() {
        try {
            Platform.startup(() -> {
            });
        } catch (Exception e) {
            // already started or headless
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = DatabaseManager.createInMemory();
        saleRepository = new SaleRepository(dbManager);
        productRepository = new ProductRepository(dbManager);
        stockMovementRepository = new StockMovementRepository(dbManager);
        inventoryService = new InventoryService(stockMovementRepository, productRepository);
        ReceiptService receiptService = new ReceiptService(productRepository);
        SalesService salesService = new SalesService(saleRepository, stockMovementRepository,
                productRepository, inventoryService, receiptService, dbManager);
        CustomerRepository customerRepository = new CustomerRepository(dbManager);
        presenter = new SalePresenter(salesService, inventoryService, productRepository, customerRepository);

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

        StockMovement entry = new StockMovement();
        entry.setProductId(1L);
        entry.setMovementType("ENTRY");
        entry.setQuantity(24);
        entry.setReferenceType("PURCHASE");
        entry.setReferenceId(1L);
        stockMovementRepository.insert(entry);
        // Update denormalized current_stock
        productRepository.updateStock(dbManager.getConnection(), 1L, 24);

        presenter.addToCart(coke, 2); // subtotal 1200
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    // ──────────────────────────────────────────────
    // Real stack — presenter throws before persistence
    // ──────────────────────────────────────────────

    @Test
    void percentageAbove100ThrowsFromPresenterAndPersistsNothing() throws SQLException {
        presenter.setSaleDiscount(150, "PERCENTAGE");

        assertThatThrownBy(() -> presenter.getDiscountedTotal())
                .isInstanceOf(SalesService.ValidationException.class);

        assertThatThrownBy(() -> presenter.completeSale())
                .isInstanceOf(SalesService.ValidationException.class);

        assertThat(saleRepository.findHistory()).isEmpty();
        assertThat(inventoryService.getCurrentStock(1L)).isEqualTo(24);
    }

    @Test
    void fixedAboveSubtotalThrowsFromPresenterAndPersistsNothing() throws SQLException {
        presenter.setSaleDiscount(5000, "FIXED");

        assertThatThrownBy(() -> presenter.getDiscountedTotal())
                .isInstanceOf(SalesService.ValidationException.class);

        assertThatThrownBy(() -> presenter.completeSale())
                .isInstanceOf(SalesService.ValidationException.class);

        assertThat(saleRepository.findHistory()).isEmpty();
        assertThat(inventoryService.getCurrentStock(1L)).isEqualTo(24);
    }

    // ──────────────────────────────────────────────
    // Real stack — valid discounts persist clamped totals
    // ──────────────────────────────────────────────

    @Test
    void validPercentageDiscountPersistsClampedTotal() throws SQLException {
        presenter.setSaleDiscount(50, "PERCENTAGE");

        assertThat(presenter.getDiscountedTotal()).isEqualTo(600.0);
        String receipt = presenter.completeSale();
        assertThat(receipt).isNotNull();

        List<Sale> history = saleRepository.findHistory();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getTotalAmount()).isEqualTo(600.0);
        assertThat(inventoryService.getCurrentStock(1L)).isEqualTo(22);
    }

    @Test
    void hundredPercentPercentageIsValidAndPersistsZeroTotal() throws SQLException {
        presenter.setSaleDiscount(100, "PERCENTAGE");

        assertThat(presenter.getDiscountedTotal()).isEqualTo(0.0);
        String receipt = presenter.completeSale();
        assertThat(receipt).isNotNull();

        List<Sale> history = saleRepository.findHistory();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getTotalAmount()).isEqualTo(0.0);
    }

    @Test
    void validFixedDiscountPersistsTotal() throws SQLException {
        presenter.setSaleDiscount(200, "FIXED");

        assertThat(presenter.getDiscountedTotal()).isEqualTo(1000.0);
        String receipt = presenter.completeSale();
        assertThat(receipt).isNotNull();

        List<Sale> history = saleRepository.findHistory();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getTotalAmount()).isEqualTo(1000.0);
    }

    // ──────────────────────────────────────────────
    // Controller wiring — discount input error handling
    // ──────────────────────────────────────────────

    @Test
    void onDiscountChangedWithInvalidPercentageShowsErrorAndResetsState() throws Exception {
        SaleController controller = controllerWithMockPresenter();
        when(mockPresenter.getDiscountedTotal())
                .thenThrow(new SalesService.ValidationException("El descuento porcentual no puede superar el 100%"));
        when(mockPresenter.getCartTotal()).thenReturn(1200.0);

        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            invokeOnDiscountChanged(controller);
            alerts.verify(() -> AlertService.showErrorDialog(eq("Descuento Inválido"), anyString()));
        }

        verify(mockPresenter, atLeastOnce()).setSaleDiscount(0, "NONE");
        assertThat(discountFieldOf(controller).getText()).isEqualTo("0");
        assertThat(discountComboOf(controller).getValue()).isEqualTo("Sin descuento");
        assertThat(totalLabelOf(controller).getText()).isEqualTo(CurrencyFormatter.format(1200.0));
    }

    @Test
    void onDiscountChangedWithInvalidFixedShowsErrorAndResetsState() throws Exception {
        SaleController controller = controllerWithMockPresenter();
        when(mockPresenter.getDiscountedTotal())
                .thenThrow(new SalesService.ValidationException("El descuento fijo no puede superar el subtotal de la venta"));
        discountComboOf(controller).getSelectionModel().select(2); // "Fijo $"

        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            invokeOnDiscountChanged(controller);
            alerts.verify(() -> AlertService.showErrorDialog(eq("Descuento Inválido"), anyString()));
        }

        verify(mockPresenter, atLeastOnce()).setSaleDiscount(0, "NONE");
        assertThat(discountFieldOf(controller).getText()).isEqualTo("0");
        assertThat(discountComboOf(controller).getValue()).isEqualTo("Sin descuento");
    }

    @Test
    void onDiscountChangedWithValidDiscountShowsNoErrorAndUpdatesTotal() throws Exception {
        SaleController controller = controllerWithMockPresenter();
        when(mockPresenter.getDiscountedTotal()).thenReturn(600.0);
        when(mockPresenter.getSaleDiscountType()).thenReturn("PERCENTAGE");
        when(mockPresenter.getSaleDiscount()).thenReturn(50.0);
        discountFieldOf(controller).setText("50");
        discountComboOf(controller).getSelectionModel().select(1); // "% Descuento"

        try (MockedStatic<AlertService> alerts = mockStatic(AlertService.class)) {
            invokeOnDiscountChanged(controller);
            alerts.verifyNoInteractions();
        }

        assertThat(totalLabelOf(controller).getText()).isEqualTo(CurrencyFormatter.format(600.0));
        assertThat(discountAmountLabelOf(controller).isVisible()).isTrue();
        assertThat(discountAmountLabelOf(controller).getText()).isEqualTo("50.0% desc.");
    }

    // ──────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────

    private SaleController controllerWithMockPresenter() throws Exception {
        SaleController controller = new SaleController();
        ComboBox<String> combo = new ComboBox<>();
        combo.setItems(FXCollections.observableArrayList("Sin descuento", "% Descuento", "Fijo $"));
        combo.getSelectionModel().selectFirst();
        TextField field = new TextField("0");
        Label amountLabel = new Label();
        Label total = new Label();
        setField(controller, "presenter", mockPresenter);
        setField(controller, "discountCombo", combo);
        setField(controller, "discountField", field);
        setField(controller, "discountAmountLabel", amountLabel);
        setField(controller, "totalLabel", total);
        return controller;
    }

    private ComboBox<String> discountComboOf(SaleController controller) {
        try {
            return (ComboBox<String>) fieldOf(controller, "discountCombo");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private TextField discountFieldOf(SaleController controller) {
        try {
            return (TextField) fieldOf(controller, "discountField");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Label discountAmountLabelOf(SaleController controller) {
        try {
            return (Label) fieldOf(controller, "discountAmountLabel");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Label totalLabelOf(SaleController controller) {
        try {
            return (Label) fieldOf(controller, "totalLabel");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void invokeOnDiscountChanged(SaleController controller) throws Exception {
        java.lang.reflect.Method m = SaleController.class.getDeclaredMethod("onDiscountChanged");
        m.setAccessible(true);
        m.invoke(controller);
    }

    private static Object fieldOf(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
