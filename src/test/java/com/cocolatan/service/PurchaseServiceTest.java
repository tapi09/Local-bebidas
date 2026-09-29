package com.cocolatan.service;

import com.cocolatan.model.Purchase;
import com.cocolatan.model.PurchaseItem;
import com.cocolatan.model.StockMovement;
import com.cocolatan.model.Supplier;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.PurchaseRepository;
import com.cocolatan.repository.StockMovementRepository;
import com.cocolatan.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private DatabaseManager databaseManager;

    @Mock
    private Connection connection;

    private PurchaseService service;

    @BeforeEach
    void setUp() {
        lenient().when(databaseManager.getConnection()).thenReturn(connection);
        service = new PurchaseService(purchaseRepository, stockMovementRepository, productRepository, supplierRepository, databaseManager);
    }

    @Test
    void savePurchaseCreatesPurchaseAndMovements() throws SQLException {
        when(purchaseRepository.saveWithItems(any(Connection.class), any(Purchase.class), anyList())).thenReturn(1L);

        Purchase purchase = createPurchase();
        PurchaseItem item = createPurchaseItem(1L, 24, 350.0);

        service.savePurchase(purchase, Collections.singletonList(item));

        verify(purchaseRepository).saveWithItems(any(Connection.class), eq(purchase), anyList());
        verify(stockMovementRepository).insert(any(Connection.class), any(StockMovement.class));
    }

    @Test
    void savePurchaseWithMultipleItemsCreatesMultipleMovements() throws SQLException {
        when(purchaseRepository.saveWithItems(any(Connection.class), any(Purchase.class), anyList())).thenReturn(1L);

        Purchase purchase = createPurchase();
        PurchaseItem item1 = createPurchaseItem(1L, 10, 100.0);
        PurchaseItem item2 = createPurchaseItem(2L, 20, 50.0);

        service.savePurchase(purchase, Arrays.asList(item1, item2));

        verify(stockMovementRepository, times(2)).insert(any(Connection.class), any(StockMovement.class));
        verify(productRepository).updateCostPrice(any(Connection.class), eq(1L), eq(100.0));
        verify(productRepository).updateCostPrice(any(Connection.class), eq(2L), eq(50.0));
    }

    @Test
    void savePurchaseUpdatesProductCostPrice() throws SQLException {
        when(purchaseRepository.saveWithItems(any(Connection.class), any(Purchase.class), anyList())).thenReturn(1L);

        Purchase purchase = createPurchase();
        PurchaseItem item = createPurchaseItem(1L, 24, 380.0);

        service.savePurchase(purchase, Collections.singletonList(item));

        verify(productRepository).updateCostPrice(any(Connection.class), eq(1L), eq(380.0));
    }

    @Test
    void savePurchaseRejectsEmptyItemList() throws SQLException {
        Purchase purchase = createPurchase();

        boolean result = service.savePurchase(purchase, Collections.emptyList());

        assertThat(result).isFalse();
        verify(purchaseRepository, never()).saveWithItems(any(Connection.class), any(), any());
    }

    @Test
    void savePurchaseRejectsNullItemList() throws SQLException {
        Purchase purchase = createPurchase();

        boolean result = service.savePurchase(purchase, null);

        assertThat(result).isFalse();
        verify(purchaseRepository, never()).saveWithItems(any(Connection.class), any(), any());
    }

    @Test
    void loadPurchaseHistoryDelegatesToRepository() throws SQLException {
        List<Purchase> history = Arrays.asList(createPurchase());
        when(purchaseRepository.findHistory(0, 0)).thenReturn(history);

        List<Purchase> result = service.loadPurchaseHistory();

        assertThat(result).hasSize(1);
        verify(purchaseRepository).findHistory(0, 0);
    }

    @Test
    void loadSuppliersForDropdown() throws SQLException {
        List<Supplier> suppliers = Arrays.asList(new Supplier());
        when(supplierRepository.findForDropdown()).thenReturn(suppliers);

        List<Supplier> result = service.loadSuppliers();

        assertThat(result).hasSize(1);
    }

    @Test
    void savePurchaseCalculatesTotalFromItems() throws SQLException {
        when(purchaseRepository.saveWithItems(any(Connection.class), any(Purchase.class), anyList())).thenAnswer(invocation -> {
            Purchase p = invocation.getArgument(1);
            List<PurchaseItem> items = invocation.getArgument(2);
            double subtotal = items.stream().mapToDouble(i -> i.getQuantity() * i.getUnitCost()).sum();
            p.setSubtotal(subtotal);
            p.setTotalAmount(subtotal + p.getTaxAmount());
            return 1L;
        });

        Purchase purchase = createPurchase();
        PurchaseItem item1 = createPurchaseItem(1L, 10, 100.0); // 1000
        PurchaseItem item2 = createPurchaseItem(2L, 5, 200.0);  // 1000

        service.savePurchase(purchase, Arrays.asList(item1, item2));

        ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).saveWithItems(any(Connection.class), captor.capture(), anyList());
        assertThat(captor.getValue().getSubtotal()).isEqualTo(2000.0);
        assertThat(captor.getValue().getTotalAmount()).isEqualTo(2000.0);
    }

    @Test
    void savePurchaseWithTaxSetsCorrectTotal() throws SQLException {
        when(purchaseRepository.saveWithItems(any(Connection.class), any(Purchase.class), anyList())).thenAnswer(invocation -> {
            Purchase p = invocation.getArgument(1);
            List<PurchaseItem> items = invocation.getArgument(2);
            double subtotal = items.stream().mapToDouble(i -> i.getQuantity() * i.getUnitCost()).sum();
            p.setSubtotal(subtotal);
            p.setTotalAmount(subtotal + p.getTaxAmount());
            return 1L;
        });

        Purchase purchase = createPurchase();
        purchase.setTaxAmount(420.0); // IVA 21%
        PurchaseItem item1 = createPurchaseItem(1L, 10, 100.0); // 1000

        service.savePurchase(purchase, Collections.singletonList(item1));

        ArgumentCaptor<Purchase> captor = ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).saveWithItems(any(Connection.class), captor.capture(), anyList());
        assertThat(captor.getValue().getSubtotal()).isEqualTo(1000.0);
        assertThat(captor.getValue().getTaxAmount()).isEqualTo(420.0);
        assertThat(captor.getValue().getTotalAmount()).isEqualTo(1420.0);
    }

    @Test
    void getSupplierNameFallsBackToPlaceholder() throws SQLException {
        when(supplierRepository.findById(5L)).thenReturn(java.util.Optional.empty());

        String result = service.getSupplierName(5L);

        assertThat(result).isEqualTo("Proveedor #5");
    }

    @Test
    void getSupplierNameReturnsNameWhenFound() throws SQLException {
        Supplier supplier = new Supplier();
        supplier.setName("Proveedor X");
        when(supplierRepository.findById(5L)).thenReturn(java.util.Optional.of(supplier));

        String result = service.getSupplierName(5L);

        assertThat(result).isEqualTo("Proveedor X");
    }

    private Purchase createPurchase() {
        Purchase purchase = new Purchase();
        purchase.setSupplierId(1L);
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
