package com.softwaredebebidas.repository;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.model.Supplier;
import com.softwaredebebidas.service.SalesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Split payment persistence: one sale paid with two methods is stored as
 * payment_method = MIXED plus two sale_payments rows summing to the rounded total.
 */
class SaleRepositorySplitPaymentTest {

    private DatabaseManager dbManager;
    private SaleRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = DatabaseManager.createInMemory();
        repository = new SaleRepository(dbManager);
        ProductRepository productRepository = new ProductRepository(dbManager);
        SupplierRepository supplierRepository = new SupplierRepository(dbManager);

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

    @Test
    void splitPersistsMixedAndTwoPaymentRowsSummingToTotal() throws SQLException {
        Sale sale = createSale("CASH", "TRANSFER", 500.0);

        Long id = repository.saveWithItems(sale, Collections.singletonList(createItem(2, 600.0)));

        assertThat(paymentMethodOf(id)).isEqualTo("MIXED");
        assertThat(sale.getPaymentMethod()).isEqualTo("MIXED");
        assertThat(sale.getPayments()).hasSize(2);
        assertThat(paymentRows(id)).containsExactly("CASH:500.0", "TRANSFER:700.0");
        assertThat(paymentSum(id)).isEqualTo(totalOf(id));
    }

    @Test
    void splitRemainderUsesRoundedTotal() throws SQLException {
        // 1200 * (1 - 33.333/100) = 800.004 -> persisted total is rounded to 800.0
        Sale sale = createSale("CASH", "DEBIT_CARD", 100.10);
        sale.setDiscount(33.333);
        sale.setDiscountType("PERCENTAGE");

        Long id = repository.saveWithItems(sale, Collections.singletonList(createItem(2, 600.0)));

        double total = totalOf(id);
        assertThat(total).isEqualTo(Math.round(total * 100) / 100.0);
        assertThat(sale.getPayments().get(0).getAmount()).isEqualTo(100.10);
        assertThat(sale.getPayments().get(1).getAmount())
                .isEqualTo(Math.round((total - 100.10) * 100) / 100.0);
        assertThat(paymentSum(id)).isEqualTo(total);
    }

    @Test
    void equalMethodsRejectedAndNothingInserted() throws SQLException {
        Sale sale = createSale("CASH", "CASH", 500.0);

        assertThatThrownBy(() -> repository.saveWithItems(sale, Collections.singletonList(createItem(2, 600.0))))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessage("Los dos medios de pago deben ser distintos.");

        assertNothingInserted();
    }

    @Test
    void firstAmountZeroRejectedAndNothingInserted() throws SQLException {
        assertFirstAmountRejected(0.0);
    }

    @Test
    void firstAmountNegativeRejectedAndNothingInserted() throws SQLException {
        assertFirstAmountRejected(-5.0);
    }

    @Test
    void firstAmountEqualToTotalRejectedAndNothingInserted() throws SQLException {
        assertFirstAmountRejected(1200.0);
    }

    @Test
    void firstAmountAboveTotalRejectedAndNothingInserted() throws SQLException {
        assertFirstAmountRejected(1500.0);
    }

    @Test
    void zeroTotalRejectedAndNothingInserted() throws SQLException {
        Sale sale = createSale("CASH", "TRANSFER", 10.0);
        sale.setDiscount(100);
        sale.setDiscountType("PERCENTAGE");

        assertThatThrownBy(() -> repository.saveWithItems(sale, Collections.singletonList(createItem(2, 600.0))))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessage("No se puede dividir el pago de una venta de $0.");

        assertNothingInserted();
    }

    @Test
    void nonSplitSaleInsertsNoPaymentRows() throws SQLException {
        Sale sale = new Sale();
        sale.setSaleDate("2026-07-22");
        sale.setChannel("IN");
        sale.setPaymentMethod("CASH");

        Long id = repository.saveWithItems(sale, Collections.singletonList(createItem(2, 600.0)));

        assertThat(paymentMethodOf(id)).isEqualTo("CASH");
        assertThat(sale.getPayments()).isEmpty();
        assertThat(count("sale_payments")).isZero();
    }

    private void assertFirstAmountRejected(double first) throws SQLException {
        Sale sale = createSale("CASH", "TRANSFER", first);

        assertThatThrownBy(() -> repository.saveWithItems(sale, Collections.singletonList(createItem(2, 600.0))))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessage("El monto del primer pago debe ser mayor a 0 y menor al total.");

        assertNothingInserted();
    }

    private void assertNothingInserted() throws SQLException {
        assertThat(count("sales")).isZero();
        assertThat(count("sale_items")).isZero();
        assertThat(count("sale_payments")).isZero();
    }

    private int count(String table) throws SQLException {
        try (Statement st = dbManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String paymentMethodOf(Long id) throws SQLException {
        try (Statement st = dbManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT payment_method FROM sales WHERE id = " + id)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private double totalOf(Long id) throws SQLException {
        try (Statement st = dbManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT total_amount FROM sales WHERE id = " + id)) {
            rs.next();
            return rs.getDouble(1);
        }
    }

    private double paymentSum(Long id) throws SQLException {
        try (Statement st = dbManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT SUM(amount) FROM sale_payments WHERE sale_id = " + id)) {
            rs.next();
            return Math.round(rs.getDouble(1) * 100) / 100.0;
        }
    }

    private List<String> paymentRows(Long id) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Statement st = dbManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT payment_method, amount FROM sale_payments WHERE sale_id = " + id + " ORDER BY id")) {
            while (rs.next()) {
                rows.add(rs.getString(1) + ":" + rs.getDouble(2));
            }
        }
        return rows;
    }

    private Sale createSale(String first, String second, double firstAmount) {
        Sale sale = new Sale();
        sale.setSaleDate("2026-07-22");
        sale.setChannel("IN");
        sale.setPaymentMethod(first);
        sale.setSplitSecondMethod(second);
        sale.setSplitFirstAmount(firstAmount);
        return sale;
    }

    private SaleItem createItem(int qty, double price) {
        SaleItem item = new SaleItem();
        item.setProductId(1L);
        item.setQuantity(qty);
        item.setUnitPrice(price);
        return item;
    }
}
