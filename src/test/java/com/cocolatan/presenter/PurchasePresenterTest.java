package com.cocolatan.presenter;

import com.cocolatan.model.Purchase;
import com.cocolatan.model.PurchaseItem;
import com.cocolatan.model.Supplier;
import com.cocolatan.service.PurchaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchasePresenterTest {

    @Mock
    private PurchaseService purchaseService;

    private PurchasePresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new PurchasePresenter(purchaseService);
    }

    @Test
    void savePurchaseDelegatesToService() {
        Purchase purchase = createPurchase();
        PurchaseItem item = createPurchaseItem(1L, 24, 350.0);
        when(purchaseService.savePurchase(purchase, Collections.singletonList(item))).thenReturn(true);

        boolean result = presenter.savePurchase(purchase, Collections.singletonList(item));

        assertThat(result).isTrue();
        verify(purchaseService).savePurchase(purchase, Collections.singletonList(item));
    }

    @Test
    void savePurchasePassesThroughFalseResult() {
        Purchase purchase = createPurchase();
        List<PurchaseItem> items = Collections.emptyList();
        when(purchaseService.savePurchase(purchase, items)).thenReturn(false);

        boolean result = presenter.savePurchase(purchase, items);

        assertThat(result).isFalse();
        verify(purchaseService).savePurchase(purchase, items);
    }

    @Test
    void loadPurchaseHistoryDelegatesToService() {
        List<Purchase> history = Arrays.asList(createPurchase());
        when(purchaseService.loadPurchaseHistory()).thenReturn(history);

        List<Purchase> result = presenter.loadPurchaseHistory();

        assertThat(result).hasSize(1);
        verify(purchaseService).loadPurchaseHistory();
    }

    @Test
    void loadSuppliersDelegatesToService() {
        List<Supplier> suppliers = Arrays.asList(new Supplier());
        when(purchaseService.loadSuppliers()).thenReturn(suppliers);

        List<Supplier> result = presenter.loadSuppliers();

        assertThat(result).hasSize(1);
        verify(purchaseService).loadSuppliers();
    }

    @Test
    void getSupplierNameDelegatesToService() {
        when(purchaseService.getSupplierName(7L)).thenReturn("Proveedor X");

        String result = presenter.getSupplierName(7L);

        assertThat(result).isEqualTo("Proveedor X");
        verify(purchaseService).getSupplierName(7L);
    }

    @Test
    void loadProductsForDropdownDelegatesToService() {
        when(purchaseService.loadProductsForDropdown()).thenReturn(Collections.emptyList());

        assertThat(presenter.loadProductsForDropdown()).isEmpty();
        verify(purchaseService).loadProductsForDropdown();
    }

    @Test
    void saveSupplierDelegatesToService() {
        Supplier supplier = new Supplier();
        when(purchaseService.saveSupplier(supplier)).thenReturn(supplier);

        assertThat(presenter.saveSupplier(supplier)).isSameAs(supplier);
        verify(purchaseService).saveSupplier(supplier);
    }

    @Test
    void getPurchaseItemsDelegatesToService() {
        List<PurchaseItem> items = Arrays.asList(createPurchaseItem(1L, 10, 100.0));
        when(purchaseService.getPurchaseItems(3L)).thenReturn(items);

        assertThat(presenter.getPurchaseItems(3L)).hasSize(1);
        verify(purchaseService).getPurchaseItems(3L);
    }

    @Test
    void calculateSubtotalReturnsSumOfItems() {
        PurchaseItem item1 = createPurchaseItem(1L, 10, 100.0);
        PurchaseItem item2 = createPurchaseItem(2L, 5, 200.0);

        double subtotal = presenter.calculateSubtotal(Arrays.asList(item1, item2));

        assertThat(subtotal).isEqualTo(2000.0);
    }

    @Test
    void calculateSubtotalReturnsZeroForEmptyList() {
        assertThat(presenter.calculateSubtotal(Collections.emptyList())).isEqualTo(0.0);
        assertThat(presenter.calculateSubtotal(null)).isEqualTo(0.0);
    }

    @Test
    void calculateTotalAddsSubtotalAndTax() {
        assertThat(presenter.calculateTotal(1000.0, 210.0)).isEqualTo(1210.0);
        assertThat(presenter.calculateTotal(0.0, 0.0)).isEqualTo(0.0);
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
