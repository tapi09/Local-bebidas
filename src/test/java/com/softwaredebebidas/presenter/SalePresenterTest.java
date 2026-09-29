package com.softwaredebebidas.presenter;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.presenter.SalePresenter;
import com.softwaredebebidas.repository.CustomerRepository;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.service.InventoryService;
import com.softwaredebebidas.service.SalesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalePresenterTest {

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
    }

    // --- Search tests ---

    @Test
    void searchProductsDelegatesToRepository() throws SQLException {
        List<Product> products = Arrays.asList(createProduct(1L, "Coca-Cola 500ml"));
        when(productRepository.searchByName("coca")).thenReturn(products);

        List<Product> results = presenter.searchProducts("coca");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Coca-Cola 500ml");
    }

    @Test
    void searchProductsReturnsAllActiveWhenQueryEmpty() throws SQLException {
        List<Product> products = Arrays.asList(createProduct(1L, "Coca-Cola"), createProduct(2L, "Pepsi"));
        when(productRepository.findAllActive()).thenReturn(products);

        List<Product> results = presenter.searchProducts("");

        assertThat(results).hasSize(2);
    }

    @Test
    void searchProductsReturnsAllActiveWhenQueryNull() throws SQLException {
        List<Product> products = Arrays.asList(createProduct(1L, "Coca-Cola"));
        when(productRepository.findAllActive()).thenReturn(products);

        List<Product> results = presenter.searchProducts(null);

        assertThat(results).hasSize(1);
    }

    // --- Barcode tests ---

    @Test
    void findByBarcodeReturnsProductWhenFound() throws SQLException {
        Product product = createProduct(1L, "Coca-Cola 500ml");
        when(productRepository.searchByBarcode("7790001001")).thenReturn(Collections.singletonList(product));

        Product result = presenter.findByBarcode("7790001001");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Coca-Cola 500ml");
    }

    @Test
    void findByBarcodeReturnsNullWhenNotFound() throws SQLException {
        when(productRepository.searchByBarcode("0000000000")).thenReturn(Collections.emptyList());

        Product result = presenter.findByBarcode("0000000000");

        assertThat(result).isNull();
    }

    // --- Cart tests ---

    @Test
    void addToCartAddsItemWhenStockValid() {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);

        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setSalePrice(600.0);

        boolean result = presenter.addToCart(product, 2);

        assertThat(result).isTrue();
        assertThat(presenter.getCartItems()).hasSize(1);
        assertThat(presenter.getCartTotal()).isEqualTo(1200.0);
    }

    @Test
    void addToCartRejectsWhenStockInvalid() {
        when(inventoryService.validateStock(1L, 5)).thenReturn(false);

        Product product = createProduct(1L, "Coca-Cola 500ml");

        assertThatThrownBy(() -> presenter.addToCart(product, 5))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("Stock insuficiente");
        assertThat(presenter.getCartItems()).isEmpty();
    }

    @Test
    void addToCartIncreasesQuantityIfAlreadyInCart() {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        when(inventoryService.validateStock(1L, 3)).thenReturn(true);
        when(inventoryService.validateStock(1L, 5)).thenReturn(true);

        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setSalePrice(600.0);

        presenter.addToCart(product, 2);
        presenter.addToCart(product, 3);

        assertThat(presenter.getCartItems()).hasSize(1);
        assertThat(presenter.getCartItems().get(0).getQuantity()).isEqualTo(5);
        assertThat(presenter.getCartTotal()).isEqualTo(3000.0);
    }

    @Test
    void addToCartRejectsWhenCumulativeQuantityExceedsStock() {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        when(inventoryService.validateStock(1L, 3)).thenReturn(true);
        when(inventoryService.validateStock(1L, 5)).thenReturn(false);

        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setSalePrice(600.0);

        presenter.addToCart(product, 2);

        assertThatThrownBy(() -> presenter.addToCart(product, 3))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("Stock insuficiente");
        assertThat(presenter.getCartItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void addToCartRejectsNullProduct() {
        boolean result = presenter.addToCart(null, 1);

        assertThat(result).isFalse();
    }

    @Test
    void addToCartRejectsZeroQuantity() {
        Product product = createProduct(1L, "Coca-Cola 500ml");

        boolean result = presenter.addToCart(product, 0);

        assertThat(result).isFalse();
    }

    @Test
    void removeFromCartRemovesItem() {
        when(inventoryService.validateStock(1L, 1)).thenReturn(true);

        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setSalePrice(600.0);
        presenter.addToCart(product, 1);

        presenter.removeFromCart(1L);

        assertThat(presenter.getCartItems()).isEmpty();
    }

    @Test
    void updateCartQuantityUpdatesCorrectly() {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        when(inventoryService.validateStock(1L, 4)).thenReturn(true);

        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setSalePrice(600.0);
        presenter.addToCart(product, 2);

        boolean result = presenter.updateCartQuantity(1L, 4);

        assertThat(result).isTrue();
        assertThat(presenter.getCartItems().get(0).getQuantity()).isEqualTo(4);
    }

    @Test
    void updateCartQuantityRemovesWhenZero() {
        when(inventoryService.validateStock(1L, 1)).thenReturn(true);

        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setSalePrice(600.0);
        presenter.addToCart(product, 1);

        presenter.updateCartQuantity(1L, 0);

        assertThat(presenter.getCartItems()).isEmpty();
    }

    @Test
    void clearCartEmptiesCart() {
        when(inventoryService.validateStock(1L, 1)).thenReturn(true);

        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setSalePrice(600.0);
        presenter.addToCart(product, 1);

        presenter.clearCart();

        assertThat(presenter.getCartItems()).isEmpty();
        assertThat(presenter.getCartTotal()).isEqualTo(0.0);
    }

    // --- Channel and payment tests ---

    @Test
    void setChannelUpdatesCurrentChannel() {
        presenter.setChannel("PEDIDOSYA");
        assertThat(presenter.getCurrentChannel()).isEqualTo("PEDIDOSYA");
    }

    @Test
    void setPaymentMethodUpdatesCurrentMethod() {
        presenter.setPaymentMethod("DEBIT_CARD");
        assertThat(presenter.getCurrentPaymentMethod()).isEqualTo("DEBIT_CARD");
    }

    // --- completeSale tests ---

    @Test
    void completeSaleReturnsReceiptWhenCartNotEmpty() {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        when(salesService.createSale(any(), anyList())).thenReturn(true);
        when(salesService.generateReceipt(any(), anyList())).thenReturn("receipt-text");

        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setSalePrice(600.0);
        presenter.addToCart(product, 2);

        String receipt = presenter.completeSale();

        assertThat(receipt).isEqualTo("receipt-text");
        verify(salesService).createSale(any(), anyList());
    }

    @Test
    void completeSaleClearsCartAfterSale() {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        when(salesService.createSale(any(), anyList())).thenReturn(true);
        when(salesService.generateReceipt(any(), anyList())).thenReturn("receipt");

        Product product = createProduct(1L, "Coca-Cola 500ml");
        product.setSalePrice(600.0);
        presenter.addToCart(product, 2);

        presenter.completeSale();

        assertThat(presenter.getCartItems()).isEmpty();
    }

    @Test
    void completeSaleReturnsNullWhenCartEmpty() {
        String receipt = presenter.completeSale();

        assertThat(receipt).isNull();
        verify(salesService, never()).createSale(any(), anyList());
    }

    @Test
    void completeSalePassesChannelAndPaymentToSale() {
        when(inventoryService.validateStock(1L, 1)).thenReturn(true);
        when(salesService.createSale(any(), anyList())).thenReturn(true);
        when(salesService.generateReceipt(any(), anyList())).thenReturn("receipt");

        presenter.setChannel("PEDIDOSYA");
        presenter.setPaymentMethod("DEBIT_CARD");

        Product product = createProduct(1L, "Pepsi 500ml");
        product.setSalePrice(550.0);
        presenter.addToCart(product, 1);

        presenter.completeSale();

        verify(salesService).createSale(argThat(sale ->
                "PEDIDOSYA".equals(sale.getChannel()) &&
                "DEBIT_CARD".equals(sale.getPaymentMethod())
        ), anyList());
    }

    @Test
    void cartTotalCalculatesCorrectly() {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        when(inventoryService.validateStock(2L, 1)).thenReturn(true);

        Product p1 = createProduct(1L, "Coca-Cola 500ml");
        p1.setSalePrice(600.0);
        Product p2 = createProduct(2L, "Pepsi 500ml");
        p2.setSalePrice(550.0);

        presenter.addToCart(p1, 2);  // 1200
        presenter.addToCart(p2, 1);  // 550

        assertThat(presenter.getCartTotal()).isEqualTo(1750.0);
    }

    private Product createProduct(Long id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setCategory("Gaseosas");
        product.setPresentation("Botella 500ml");
        product.setSalePrice(600.0);
        product.setActive(true);
        return product;
    }
}
