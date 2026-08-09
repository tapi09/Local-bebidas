package com.cocolatan.repository;

import com.cocolatan.model.Product;
import com.cocolatan.model.Sale;
import com.cocolatan.model.SaleItem;
import com.cocolatan.model.Supplier;
import com.cocolatan.service.SalesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaleRepositoryTest {

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

        // Create a supplier for foreign key
        Supplier supplier = new Supplier();
        supplier.setName("Distribuidora Norte");
        supplierRepository.save(supplier);

        // Create products for foreign key
        Product product1 = new Product();
        product1.setName("Coca-Cola 500ml");
        product1.setCategory("Gaseosas");
        product1.setPresentation("Botella 500ml");
        product1.setCostPrice(350.0);
        product1.setSalePrice(600.0);
        product1.setSupplierId(1L);
        product1.setBarcode("7790001001");
        product1.setMinStock(5);
        productRepository.save(product1);

        Product product2 = new Product();
        product2.setName("Pepsi 500ml");
        product2.setCategory("Gaseosas");
        product2.setPresentation("Botella 500ml");
        product2.setCostPrice(300.0);
        product2.setSalePrice(550.0);
        product2.setSupplierId(1L);
        product2.setBarcode("7790002002");
        product2.setMinStock(5);
        productRepository.save(product2);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void saveWithItemsReturnsSaleId() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 2, 600.0);

        Long id = repository.saveWithItems(sale, Collections.singletonList(item));

        assertThat(id).isNotNull();
        assertThat(id).isGreaterThan(0);
    }

    @Test
    void saveWithItemsCreatesSaleRecord() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 2, 600.0);

        Long id = repository.saveWithItems(sale, Collections.singletonList(item));

        Optional<Sale> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getChannel()).isEqualTo("IN");
        assertThat(found.get().getPaymentMethod()).isEqualTo("CASH");
        assertThat(found.get().getTotalAmount()).isEqualTo(1200.0);
    }

    @Test
    void saveWithItemsCreatesSaleItems() throws SQLException {
        Sale sale = createSale("PEDIDOSYA", "DEBIT_CARD");
        SaleItem item1 = createSaleItem(1L, 2, 600.0);
        SaleItem item2 = createSaleItem(2L, 3, 550.0);

        Long id = repository.saveWithItems(sale, Arrays.asList(item1, item2));

        List<SaleItem> items = repository.findItemsBySaleId(id);
        assertThat(items).hasSize(2);
    }

    @Test
    void saveWithItemsCalculatesTotalAmount() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        SaleItem item1 = createSaleItem(1L, 2, 600.0);  // 1200
        SaleItem item2 = createSaleItem(2L, 3, 550.0);  // 1650

        Long id = repository.saveWithItems(sale, Arrays.asList(item1, item2));

        Optional<Sale> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getTotalAmount()).isEqualTo(2850.0);
    }

    @Test
    void saveWithItemsCalculatesItemSubtotals() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 3, 600.0);

        Long id = repository.saveWithItems(sale, Collections.singletonList(item));

        List<SaleItem> items = repository.findItemsBySaleId(id);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSubtotal()).isEqualTo(1800.0);
    }

    @Test
    @DisplayName("saveWithItems() descuenta el porcentaje del total de la venta")
    void saveWithItemsAppliesPercentageDiscountToTotal() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        sale.setDiscount(10.0);
        sale.setDiscountType("PERCENTAGE");
        SaleItem item1 = createSaleItem(1L, 2, 600.0);  // 1200
        SaleItem item2 = createSaleItem(2L, 3, 550.0);  // 1650

        Long id = repository.saveWithItems(sale, Arrays.asList(item1, item2));

        Optional<Sale> found = repository.findById(id);
        assertThat(found).isPresent();
        // 2850 * 0.9 = 2565
        assertThat(found.get().getTotalAmount()).isEqualTo(2565.0);
    }

    @Test
    @DisplayName("saveWithItems() descuenta el monto fijo del total de la venta")
    void saveWithItemsAppliesFixedDiscountToTotal() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        sale.setDiscount(100.0);
        sale.setDiscountType("FIXED");
        SaleItem item1 = createSaleItem(1L, 2, 600.0);  // 1200
        SaleItem item2 = createSaleItem(2L, 3, 550.0);  // 1650

        Long id = repository.saveWithItems(sale, Arrays.asList(item1, item2));

        Optional<Sale> found = repository.findById(id);
        assertThat(found).isPresent();
        // 2850 - 100 = 2750
        assertThat(found.get().getTotalAmount()).isEqualTo(2750.0);
    }

    @Test
    @DisplayName("saveWithItems() rechaza descuento fijo mayor al subtotal (REQ-DISC-04)")
    void saveWithItemsFixedDiscountAboveSubtotalRejected() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        sale.setDiscount(5000.0);
        sale.setDiscountType("FIXED");
        SaleItem item = createSaleItem(1L, 2, 600.0);  // 1200

        assertThatThrownBy(() -> repository.saveWithItems(sale, Collections.singletonList(item)))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("subtotal");

        assertThat(repository.findHistory()).isEmpty();
    }

    @Test
    void findHistoryReturnsSalesDescending() throws SQLException {
        Sale s1 = createSale("IN", "CASH");
        s1.setSaleDate("01/07/2026");
        repository.saveWithItems(s1, Collections.singletonList(createSaleItem(1L, 1, 600.0)));

        Sale s2 = createSale("PEDIDOSYA", "DEBIT_CARD");
        s2.setSaleDate("15/07/2026");
        repository.saveWithItems(s2, Collections.singletonList(createSaleItem(2L, 2, 550.0)));

        List<Sale> history = repository.findHistory();

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getSaleDate()).isEqualTo("15/07/2026");
        assertThat(history.get(1).getSaleDate()).isEqualTo("01/07/2026");
    }

    @Test
    void findHistoryReturnsEmptyWhenNoSales() throws SQLException {
        List<Sale> history = repository.findHistory();
        assertThat(history).isEmpty();
    }

    @Test
    void findItemsBySaleIdReturnsCorrectItems() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 2, 600.0);

        Long saleId = repository.saveWithItems(sale, Collections.singletonList(item));

        List<SaleItem> items = repository.findItemsBySaleId(saleId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getProductId()).isEqualTo(1L);
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
        assertThat(items.get(0).getUnitPrice()).isEqualTo(600.0);
    }

    @Test
    @DisplayName("findById() retorna empty para ID de sale inexistente")
    void findByIdReturnsEmptyForNonexistent() throws SQLException {
        Optional<Sale> found = repository.findById(999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("saveWithItems() persiste items con descuento")
    void saveWithItemsWithDiscounts() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 2, 600.0);
        item.setDiscount(10.0);
        item.setDiscountType("PERCENTAGE");

        Long id = repository.saveWithItems(sale, Collections.singletonList(item));

        List<SaleItem> items = repository.findItemsBySaleId(id);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getDiscount()).isEqualTo(10.0);
        assertThat(items.get(0).getDiscountType()).isEqualTo("PERCENTAGE");
    }

    @Test
    @DisplayName("saveWithItems() hace rollback en caso de error")
    void saveWithItemsRollbackOnError() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(999L, 1, 100.0);

        assertThatThrownBy(() -> repository.saveWithItems(sale, Collections.singletonList(item)))
                .isInstanceOf(SQLException.class);

        Optional<Sale> found = repository.findById(1L);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("updateReceipt persiste receipt_text y no lo sobrescribe si ya existe")
    void updateReceiptPersistsAndDoesNotOverwrite() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        Long saleId = repository.saveWithItems(sale, Collections.singletonList(createSaleItem(1L, 1, 600.0)));
        Connection conn = dbManager.getConnection();

        repository.updateReceipt(conn, saleId, "Primer comprobante");

        Optional<Sale> found = repository.findById(saleId);
        assertThat(found).isPresent();
        assertThat(found.get().getReceiptText()).isEqualTo("Primer comprobante");

        // Second call should NOT overwrite (receipt_text IS NULL check)
        repository.updateReceipt(conn, saleId, "Segundo comprobante");

        found = repository.findById(saleId);
        assertThat(found.get().getReceiptText()).isEqualTo("Primer comprobante");
    }

    @Test
    @DisplayName("sumSalesForDate suma el total de ventas activas de la fecha")
    void sumSalesForDateReturnsTotalOfActiveSalesForThatDate() throws SQLException {
        Sale s1 = createSale("IN", "CASH");
        s1.setSaleDate("22/07/2026");
        repository.saveWithItems(s1, Collections.singletonList(createSaleItem(1L, 2, 600.0)));

        Sale s2 = createSale("PEDIDOSYA", "DEBIT_CARD");
        s2.setSaleDate("22/07/2026");
        repository.saveWithItems(s2, Collections.singletonList(createSaleItem(2L, 1, 550.0)));

        double result = repository.sumSalesForDate("22/07/2026");

        assertThat(result).isEqualTo(1750.0);
    }

    @Test
    @DisplayName("sumSalesForDate excluye ventas canceladas y otras fechas")
    void sumSalesForDateExcludesCancelledAndOtherDates() throws SQLException {
        Sale active = createSale("IN", "CASH");
        active.setSaleDate("22/07/2026");
        repository.saveWithItems(active, Collections.singletonList(createSaleItem(1L, 2, 600.0)));

        Sale cancelled = createSale("PEDIDOSYA", "DEBIT_CARD");
        cancelled.setSaleDate("22/07/2026");
        Long cancelledId = repository.saveWithItems(cancelled, Collections.singletonList(createSaleItem(2L, 1, 550.0)));
        repository.updateStatus(dbManager.getConnection(), cancelledId, "CANCELLED", "22/07/2026 23:00", "Cancelado");

        Sale otherDate = createSale("IN", "CASH");
        otherDate.setSaleDate("21/07/2026");
        repository.saveWithItems(otherDate, Collections.singletonList(createSaleItem(1L, 3, 100.0)));

        double result = repository.sumSalesForDate("22/07/2026");

        assertThat(result).isEqualTo(1200.0);
    }

    @Test
    @DisplayName("sumSalesForDate devuelve cero cuando no hay ventas para la fecha")
    void sumSalesForDateReturnsZeroWhenNoSalesForThatDate() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        sale.setSaleDate("22/07/2026");
        repository.saveWithItems(sale, Collections.singletonList(createSaleItem(1L, 1, 600.0)));

        double result = repository.sumSalesForDate("01/01/2020");

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("updateStatus cambia status a CANCELLED con motivo")
    void updateStatusCancelsSale() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 2, 600.0);
        Long saleId = repository.saveWithItems(sale, Collections.singletonList(item));
        Connection conn = dbManager.getConnection();

        repository.updateStatus(conn, saleId, "CANCELLED", "27/07/2026 22:00", "Prueba anulación");

        Optional<Sale> found = repository.findById(saleId);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("CANCELLED");
        assertThat(found.get().getCancelledAt()).isEqualTo("27/07/2026 22:00");
        assertThat(found.get().getCancellationReason()).isEqualTo("Prueba anulación");
    }

    @Test
    @DisplayName("findAllActive excluye ventas canceladas")
    void findAllActiveExcludesCancelled() throws SQLException {
        Sale s1 = createSale("IN", "CASH");
        s1.setSaleDate("01/07/2026");
        Long id1 = repository.saveWithItems(s1, Collections.singletonList(createSaleItem(1L, 1, 600.0)));

        Sale s2 = createSale("PEDIDOSYA", "DEBIT_CARD");
        s2.setSaleDate("15/07/2026");
        Long id2 = repository.saveWithItems(s2, Collections.singletonList(createSaleItem(2L, 2, 550.0)));

        // Cancel the second sale
        Connection conn = dbManager.getConnection();
        repository.updateStatus(conn, id2, "CANCELLED", "27/07/2026 22:00", "Cancelado en test");

        List<Sale> active = repository.findAllActive();

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getId()).isEqualTo(id1);
    }

    @Test
    @DisplayName("findAllActive retorna vacío cuando todas canceladas")
    void findAllActiveReturnsEmptyWhenAllCancelled() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        Long saleId = repository.saveWithItems(sale, Collections.singletonList(createSaleItem(1L, 1, 600.0)));

        Connection conn = dbManager.getConnection();
        repository.updateStatus(conn, saleId, "CANCELLED", "27/07/2026 22:00", "Cancelado");

        List<Sale> active = repository.findAllActive();
        assertThat(active).isEmpty();
    }

    @Test
    @DisplayName("saveWithItems hace rollback de toda la venta si un ítem viola FK")
    void saveWithItemsRollsBackOnForeignKeyViolation() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        SaleItem valid = createSaleItem(1L, 2, 600.0);
        SaleItem invalid = createSaleItem(999L, 1, 550.0);

        assertThatThrownBy(() -> repository.saveWithItems(sale, Arrays.asList(valid, invalid)))
                .isInstanceOf(SQLException.class);

        assertThat(repository.findHistory()).isEmpty();
    }

    @Test
    @DisplayName("saveWithItems no persiste ventas de una operación fallida")
    void saveWithItemsDoesNotPersistOnRollback() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        SaleItem valid = createSaleItem(1L, 2, 600.0);
        SaleItem invalid = createSaleItem(999L, 1, 550.0);

        assertThatThrownBy(() -> repository.saveWithItems(sale, Arrays.asList(valid, invalid)))
                .isInstanceOf(SQLException.class);

        assertThat(repository.findAllActive()).isEmpty();
    }

    private Sale createSale(String channel, String paymentMethod) {
        Sale sale = new Sale();
        sale.setSaleDate("22/07/2026");
        sale.setChannel(channel);
        sale.setPaymentMethod(paymentMethod);
        return sale;
    }

    private SaleItem createSaleItem(Long productId, int quantity, double unitPrice) {
        SaleItem item = new SaleItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        return item;
    }
}
