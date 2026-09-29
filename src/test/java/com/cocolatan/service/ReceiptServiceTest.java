package com.cocolatan.service;

import com.cocolatan.model.Product;
import com.cocolatan.model.Sale;
import com.cocolatan.model.SaleItem;
import com.cocolatan.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        receiptService = new ReceiptService(productRepository);
    }

    @Test
    void generateReceiptContainsStoreName() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Coca-Cola 500ml")));

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 2, 600.0, 1200.0);

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Cocolatán");
        assertThat(receipt).contains("Central de Bebidas");
    }

    @Test
    void generateReceiptContainsDateAndChannel() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Pepsi 500ml")));

        Sale sale = createSale("PEDIDOSYA", "DEBIT_CARD");
        sale.setSaleDate("2026-07-22");
        SaleItem item = createSaleItem(1L, 1, 550.0, 550.0);

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("22/07/2026");
        assertThat(receipt).contains("PedidosYa");
        assertThat(receipt).contains("Tarjeta de Débito");
    }

    @Test
    @DisplayName("generateReceipt debe mostrar la fecha en dd/MM/yyyy aunque sale_date esté persistida en ISO")
    void generateReceiptFormatsIsoDateForDisplay() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Coca-Cola 500ml")));

        Sale sale = createSale("IN", "CASH");
        // Real production value: SalesService stamps sale_date as ISO ("yyyy-MM-dd"), not
        // dd/MM/yyyy. The receipt must still show it in the user-facing format.
        sale.setSaleDate("2026-09-13");
        SaleItem item = createSaleItem(1L, 1, 600.0, 600.0);

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt)
                .as("El comprobante no debería mostrar la fecha ISO cruda al cliente")
                .contains("13/09/2026")
                .doesNotContain("2026-09-13");
    }

    @Test
    void generateReceiptContainsProductDetails() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Agua Villavicencio")));

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 3, 400.0, 1200.0);

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Agua Villavicencio");
        assertThat(receipt).contains("x3");
    }

    @Test
    void generateReceiptContainsTotal() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Coca-Cola 500ml")));

        Sale sale = createSale("IN", "CASH");
        sale.setTotalAmount(2400.0);
        SaleItem item = createSaleItem(1L, 4, 600.0, 2400.0);

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("TOTAL");
        assertThat(receipt).contains("2.400");
    }

    @Test
    void generateReceiptWithMultipleItems() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Coca-Cola 500ml")));
        when(productRepository.findById(2L)).thenReturn(Optional.of(createProduct("Pepsi 500ml")));

        Sale sale = createSale("IN", "CASH");
        sale.setTotalAmount(2750.0);
        SaleItem item1 = createSaleItem(1L, 2, 600.0, 1200.0);
        SaleItem item2 = createSaleItem(2L, 3, 550.0, 1650.0);

        String receipt = receiptService.generateReceipt(sale, Arrays.asList(item1, item2));

        assertThat(receipt).contains("Coca-Cola 500ml");
        assertThat(receipt).contains("Pepsi 500ml");
        assertThat(receipt).contains("2.750");
    }

    @Test
    void generateReceiptFormatsChannelAsLocal() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Test")));

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 1, 100.0, 100.0);

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Local");
    }

    @Test
    void generateReceiptFormatsPaymentAsCash() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Test")));

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 1, 100.0, 100.0);

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Efectivo");
    }

    @Test
    void generateReceiptContainsThankYouMessage() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Test")));

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 1, 100.0, 100.0);

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Gracias por su compra");
    }

    @Test
    @DisplayName("generateReceipt incluye descuento a nivel venta cuando corresponde")
    void generateReceipt_withSaleDiscount() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Coca-Cola 500ml")));

        Sale sale = createSale("IN", "CASH");
        sale.setTotalAmount(1080.0);
        sale.setDiscount(10.0);
        sale.setDiscountType("PERCENTAGE");
        SaleItem item = createSaleItem(1L, 2, 600.0, 1200.0);

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Descuento venta: 10%");
    }

    @Test
    @DisplayName("generateReceipt incluye descuento por item cuando corresponde")
    void generateReceipt_withItemDiscount() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Pepsi 500ml")));

        Sale sale = createSale("IN", "CASH");
        sale.setTotalAmount(1200.0);
        SaleItem item = createSaleItem(1L, 2, 600.0, 1200.0);
        item.setDiscount(15.0);
        item.setDiscountType("PERCENTAGE");

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Descuento: 15%");
    }

    @Test
    @DisplayName("generateReceipt muestra el descuento fijo de venta como monto, no como porcentaje")
    void generateReceipt_withFixedSaleDiscount() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Coca-Cola 500ml")));

        Sale sale = createSale("IN", "CASH");
        sale.setTotalAmount(1100.0);
        sale.setDiscount(100.0);
        sale.setDiscountType("FIXED");
        SaleItem item = createSaleItem(1L, 2, 600.0, 1200.0);

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Descuento venta: -$100,00");
        assertThat(receipt).doesNotContain("Descuento venta: 100%");
    }

    @Test
    @DisplayName("generateReceipt muestra el descuento fijo por item como monto, no como porcentaje")
    void generateReceipt_withFixedItemDiscount() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Pepsi 500ml")));

        Sale sale = createSale("IN", "CASH");
        sale.setTotalAmount(1100.0);
        SaleItem item = createSaleItem(1L, 2, 600.0, 1200.0);
        item.setDiscount(50.0);
        item.setDiscountType("FIXED");

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Descuento: -$50,00");
        assertThat(receipt).doesNotContain("Descuento: 50%");
    }

    @Test
    void generateReceiptShowsHierarchyLabelBesideName() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Quilmes", "Cervezas", "Latas")));

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 1, 600.0, 600.0);

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Quilmes — Cervezas — Latas");
    }

    @Test
    void generateReceiptOmitsLabelForUncategorizedProduct() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Agua Villavicencio")));

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 1, 400.0, 400.0);

        String receipt = receiptService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Agua Villavicencio");
        assertThat(receipt).doesNotContain(" — ");
    }

    private Sale createSale(String channel, String paymentMethod) {
        Sale sale = new Sale();
        sale.setSaleDate("2026-07-22");
        sale.setChannel(channel);
        sale.setPaymentMethod(paymentMethod);
        sale.setTotalAmount(0);
        return sale;
    }

    private SaleItem createSaleItem(Long productId, int quantity, double unitPrice, double subtotal) {
        SaleItem item = new SaleItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setSubtotal(subtotal);
        return item;
    }

    private Product createProduct(String name) {
        Product product = new Product();
        product.setId(1L);
        product.setName(name);
        return product;
    }

    private Product createProduct(String name, String categoryName, String subcategoryName) {
        Product product = createProduct(name);
        product.setCategoryName(categoryName);
        product.setSubcategoryName(subcategoryName);
        return product;
    }
}
