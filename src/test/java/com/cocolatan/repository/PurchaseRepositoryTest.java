package com.cocolatan.repository;

import com.cocolatan.model.Purchase;
import com.cocolatan.model.PurchaseItem;
import com.cocolatan.model.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseRepositoryTest {

    private DatabaseManager dbManager;
    private PurchaseRepository repository;
    private SupplierRepository supplierRepository;
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = DatabaseManager.createInMemory();
        repository = new PurchaseRepository(dbManager);
        supplierRepository = new SupplierRepository(dbManager);
        productRepository = new ProductRepository(dbManager);

        // Create a supplier for foreign key
        Supplier supplier = new Supplier();
        supplier.setName("Distribuidora Norte");
        supplierRepository.save(supplier);

        // Create products for foreign key
        for (int i = 1; i <= 3; i++) {
            com.cocolatan.model.Product product = new com.cocolatan.model.Product();
            product.setName("Product " + i);
            product.setCategory("Test");
            product.setPresentation("Unit");
            product.setCostPrice(100.0 * i);
            product.setSalePrice(200.0 * i);
            product.setMinStock(5);
            productRepository.save(product);
        }
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void saveWithItemsReturnsPurchaseId() throws SQLException {
        Purchase purchase = createPurchase(1L);
        PurchaseItem item = createPurchaseItem(1L, 24, 350.0);

        Long id = repository.saveWithItems(purchase, Collections.singletonList(item));

        assertThat(id).isNotNull();
        assertThat(id).isGreaterThan(0);
    }

    @Test
    void saveWithItemsCreatesPurchaseRecord() throws SQLException {
        Purchase purchase = createPurchase(1L);
        PurchaseItem item = createPurchaseItem(1L, 24, 350.0);

        Long id = repository.saveWithItems(purchase, Collections.singletonList(item));

        Optional<Purchase> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getSupplierId()).isEqualTo(1L);
        assertThat(found.get().getSubtotal()).isEqualTo(8400.0);
        assertThat(found.get().getTaxAmount()).isEqualTo(0.0);
        assertThat(found.get().getTotalAmount()).isEqualTo(8400.0);
    }

    @Test
    void saveWithItemsCreatesPurchaseItems() throws SQLException {
        Purchase purchase = createPurchase(1L);
        PurchaseItem item1 = createPurchaseItem(1L, 24, 350.0);
        PurchaseItem item2 = createPurchaseItem(2L, 10, 500.0);

        Long id = repository.saveWithItems(purchase, Arrays.asList(item1, item2));

        List<PurchaseItem> items = repository.findItemsByPurchaseId(id);
        assertThat(items).hasSize(2);
    }

    @Test
    void saveWithItemsCalculatesTotalAmount() throws SQLException {
        Purchase purchase = createPurchase(1L);
        PurchaseItem item1 = createPurchaseItem(1L, 10, 100.0);  // 1000
        PurchaseItem item2 = createPurchaseItem(2L, 20, 50.0);   // 1000

        Long id = repository.saveWithItems(purchase, Arrays.asList(item1, item2));

        Optional<Purchase> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getTotalAmount()).isEqualTo(2000.0);
    }

    @Test
    void findHistoryReturnsPurchasesDescending() throws SQLException {
        // Create two purchases
        Purchase p1 = createPurchase(1L);
        p1.setPurchaseDate("01/07/2026");
        repository.saveWithItems(p1, Collections.singletonList(createPurchaseItem(1L, 10, 100.0)));

        Purchase p2 = createPurchase(1L);
        p2.setPurchaseDate("15/07/2026");
        repository.saveWithItems(p2, Collections.singletonList(createPurchaseItem(1L, 5, 200.0)));

        List<Purchase> history = repository.findHistory();

        assertThat(history).hasSize(2);
        // Most recent first
        assertThat(history.get(0).getPurchaseDate()).isEqualTo("15/07/2026");
        assertThat(history.get(1).getPurchaseDate()).isEqualTo("01/07/2026");
    }

    @Test
    void findHistoryReturnsEmptyWhenNoPurchases() throws SQLException {
        List<Purchase> history = repository.findHistory();
        assertThat(history).isEmpty();
    }

    @Test
    void findHistorySortsByDateDescending() throws SQLException {
        Purchase p1 = createPurchase(1L);
        p1.setPurchaseDate("2026-01-15");
        repository.saveWithItems(p1, Collections.singletonList(createPurchaseItem(1L, 10, 100.0)));

        Purchase p2 = createPurchase(1L);
        p2.setPurchaseDate("2025-12-02");
        repository.saveWithItems(p2, Collections.singletonList(createPurchaseItem(1L, 5, 200.0)));

        Purchase p3 = createPurchase(1L);
        p3.setPurchaseDate("2026-03-20");
        repository.saveWithItems(p3, Collections.singletonList(createPurchaseItem(1L, 5, 200.0)));

        List<Purchase> history = repository.findHistory();

        assertThat(history).hasSize(3);
        // Newest first, independent of insertion order (ISO-8601 format)
        assertThat(history.get(0).getPurchaseDate()).isEqualTo("2026-03-20");
        assertThat(history.get(1).getPurchaseDate()).isEqualTo("2026-01-15");
        assertThat(history.get(2).getPurchaseDate()).isEqualTo("2025-12-02");
    }

    @Test
    void findItemsByProductIdsReturnsItemsForMultipleProducts() throws SQLException {
        Purchase purchase = createPurchase(1L);
        PurchaseItem item1 = createPurchaseItem(1L, 10, 100.0);
        PurchaseItem item2 = createPurchaseItem(2L, 5, 200.0);
        repository.saveWithItems(purchase, Arrays.asList(item1, item2));

        List<PurchaseItem> items = repository.findItemsByProductIds(Arrays.asList(1L, 2L));

        assertThat(items).hasSize(2);
        assertThat(items).extracting(PurchaseItem::getProductId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void findItemsByProductIdsReturnsEmptyForEmptyList() throws SQLException {
        assertThat(repository.findItemsByProductIds(Collections.emptyList())).isEmpty();
    }

    @Test
    void findItemsByPurchaseIdReturnsCorrectItems() throws SQLException {
        Purchase purchase = createPurchase(1L);
        PurchaseItem item = createPurchaseItem(1L, 24, 350.0);
        item.setLotNumber("LOT-001");
        item.setExpiryDate("31/12/2026");

        Long purchaseId = repository.saveWithItems(purchase, Collections.singletonList(item));

        List<PurchaseItem> items = repository.findItemsByPurchaseId(purchaseId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getLotNumber()).isEqualTo("LOT-001");
        assertThat(items.get(0).getExpiryDate()).isEqualTo("31/12/2026");
    }

    @Test
    void saveWithMultipleItemsAllPersisted() throws SQLException {
        Purchase purchase = createPurchase(1L);
        PurchaseItem item1 = createPurchaseItem(1L, 10, 100.0);
        PurchaseItem item2 = createPurchaseItem(2L, 20, 50.0);
        PurchaseItem item3 = createPurchaseItem(3L, 5, 200.0);

        Long id = repository.saveWithItems(purchase, Arrays.asList(item1, item2, item3));

        List<PurchaseItem> items = repository.findItemsByPurchaseId(id);
        assertThat(items).hasSize(3);
    }

    @Test
    @DisplayName("findById() retorna empty para ID de purchase inexistente")
    void findByIdReturnsEmptyForNonexistent() throws SQLException {
        Optional<Purchase> found = repository.findById(999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findItemsByProductId() retorna items de todas las purchases")
    void findItemsByProductIdReturnsAcrossPurchases() throws SQLException {
        Purchase p1 = createPurchase(1L);
        PurchaseItem item1 = createPurchaseItem(1L, 10, 100.0);
        repository.saveWithItems(p1, java.util.Collections.singletonList(item1));

        Purchase p2 = createPurchase(1L);
        PurchaseItem item2 = createPurchaseItem(1L, 5, 200.0);
        repository.saveWithItems(p2, java.util.Collections.singletonList(item2));

        List<PurchaseItem> items = repository.findItemsByProductId(1L);

        assertThat(items).hasSize(2);
        assertThat(items).allMatch(i -> i.getProductId().equals(1L));
    }

    @Test
    void saveWithItemsPersistsSubtotalAndTax() throws SQLException {
        Purchase purchase = createPurchase(1L);
        purchase.setTaxAmount(210.0); // IVA 21%
        PurchaseItem item1 = createPurchaseItem(1L, 10, 100.0); // 1000
        PurchaseItem item2 = createPurchaseItem(2L, 5, 200.0);  // 1000

        Long id = repository.saveWithItems(purchase, Arrays.asList(item1, item2));

        Optional<Purchase> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getSubtotal()).isEqualTo(2000.0);
        assertThat(found.get().getTaxAmount()).isEqualTo(210.0);
        assertThat(found.get().getTotalAmount()).isEqualTo(2210.0);
    }

    @Test
    void saveWithItemsPersistsPaymentMethod() throws SQLException {
        Purchase purchase = createPurchase(1L);
        purchase.setPaymentMethod("Transferencia");
        PurchaseItem item = createPurchaseItem(1L, 10, 100.0);

        Long id = repository.saveWithItems(purchase, Collections.singletonList(item));

        Optional<Purchase> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getPaymentMethod()).isEqualTo("Transferencia");
    }

    @Test
    void saveWithItemsPersistsNullPaymentMethod() throws SQLException {
        Purchase purchase = createPurchase(1L);
        PurchaseItem item = createPurchaseItem(1L, 10, 100.0);

        Long id = repository.saveWithItems(purchase, Collections.singletonList(item));

        Optional<Purchase> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getPaymentMethod()).isNull();
    }

    @Test
    @DisplayName("saveWithItems hace rollback de toda la compra si un ítem viola FK")
    void saveWithItemsRollsBackOnForeignKeyViolation() throws SQLException {
        Purchase purchase = createPurchase(1L);
        PurchaseItem valid = createPurchaseItem(1L, 10, 100.0);
        PurchaseItem invalid = createPurchaseItem(999L, 5, 50.0);

        assertThatThrownBy(() -> repository.saveWithItems(purchase, Arrays.asList(valid, invalid)))
                .isInstanceOf(SQLException.class);

        assertThat(repository.findHistory()).isEmpty();
    }

    @Test
    @DisplayName("saveWithItems no persiste ítems de una compra fallida")
    void saveWithItemsDoesNotPersistItemsOnRollback() throws SQLException {
        Purchase purchase = createPurchase(1L);
        PurchaseItem valid = createPurchaseItem(1L, 10, 100.0);
        PurchaseItem invalid = createPurchaseItem(999L, 5, 50.0);

        assertThatThrownBy(() -> repository.saveWithItems(purchase, Arrays.asList(valid, invalid)))
                .isInstanceOf(SQLException.class);

        assertThat(repository.findItemsByProductId(1L)).isEmpty();
    }

    private Purchase createPurchase(Long supplierId) {
        Purchase purchase = new Purchase();
        purchase.setSupplierId(supplierId);
        purchase.setInvoiceRef("INV-001");
        purchase.setPurchaseDate("22/07/2026");
        purchase.setNotes("Test purchase");
        return purchase;
    }

    private PurchaseItem createPurchaseItem(Long productId, int quantity, double unitCost) {
        PurchaseItem item = new PurchaseItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitCost(unitCost);
        return item;
    }
}
