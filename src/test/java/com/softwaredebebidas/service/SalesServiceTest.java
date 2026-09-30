package com.softwaredebebidas.service;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.model.StockMovement;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.SaleRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private DatabaseManager databaseManager;

    @Mock
    private Connection connection;

    private ReceiptService receiptService;
    private SalesService salesService;

    @BeforeEach
    void setUp() {
        lenient().when(databaseManager.getConnection()).thenReturn(connection);
        receiptService = new ReceiptService(productRepository);
        salesService = new SalesService(saleRepository, stockMovementRepository, productRepository, inventoryService, receiptService, databaseManager);
    }

    // --- createSale tests ---

    @Test
    void createSaleCreatesSaleAndMovements() throws SQLException {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        when(saleRepository.saveWithItems(any(Connection.class), any(Sale.class), anyList())).thenReturn(1L);

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 2, 600.0);

        boolean result = salesService.createSale(sale, Collections.singletonList(item));

        assertThat(result).isTrue();
        verify(saleRepository).saveWithItems(any(Connection.class), eq(sale), anyList());
        verify(stockMovementRepository).insert(any(Connection.class), any(StockMovement.class));
        verify(saleRepository).updateReceipt(any(Connection.class), eq(1L), anyString());
    }

    @Test
    void createSaleWithMultipleItemsCreatesMultipleMovements() throws SQLException {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        when(inventoryService.validateStock(2L, 3)).thenReturn(true);
        when(saleRepository.saveWithItems(any(Connection.class), any(Sale.class), anyList())).thenReturn(1L);

        Sale sale = createSale("IN", "CASH");
        SaleItem item1 = createSaleItem(1L, 2, 600.0);
        SaleItem item2 = createSaleItem(2L, 3, 550.0);

        salesService.createSale(sale, Arrays.asList(item1, item2));

        verify(stockMovementRepository, times(2)).insert(any(Connection.class), any(StockMovement.class));
        verify(saleRepository).updateReceipt(any(Connection.class), eq(1L), anyString());
    }

    @Test
    void createSaleSetsEXITMovementType() throws SQLException {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        when(saleRepository.saveWithItems(any(Connection.class), any(Sale.class), anyList())).thenReturn(1L);

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 2, 600.0);

        salesService.createSale(sale, Collections.singletonList(item));

        verify(stockMovementRepository).insert(any(Connection.class), argThat(movement ->
                "EXIT".equals(movement.getMovementType()) &&
                "SALE".equals(movement.getReferenceType()) &&
                movement.getQuantity() == 2
        ));
        verify(saleRepository).updateReceipt(any(Connection.class), eq(1L), anyString());
    }

    @Test
    void createSaleSetsSaleReferenceOnMovement() throws SQLException {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        when(saleRepository.saveWithItems(any(Connection.class), any(Sale.class), anyList())).thenReturn(42L);

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 2, 600.0);

        salesService.createSale(sale, Collections.singletonList(item));

        verify(stockMovementRepository).insert(any(Connection.class), argThat(movement ->
                movement.getReferenceId() != null && movement.getReferenceId() == 42L
        ));
        verify(saleRepository).updateReceipt(any(Connection.class), eq(42L), anyString());
    }

    @Test
    void createSaleRejectsEmptyItemList() {
        Sale sale = createSale("IN", "CASH");

        assertThatThrownBy(() -> salesService.createSale(sale, Collections.emptyList()))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("al menos un producto");
    }

    @Test
    void createSaleRejectsNullItemList() {
        Sale sale = createSale("IN", "CASH");

        assertThatThrownBy(() -> salesService.createSale(sale, null))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("al menos un producto");
    }

    @Test
    void createSaleRejectsInsufficientStock() throws SQLException {
        when(inventoryService.validateStock(1L, 5)).thenReturn(false);
        when(inventoryService.getAvailableStock(1L)).thenReturn(3);
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Coca-Cola 500ml")));

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 5, 600.0);

        assertThatThrownBy(() -> salesService.createSale(sale, Collections.singletonList(item)))
                .isInstanceOf(SalesService.ValidationException.class)
                .hasMessageContaining("Stock insuficiente")
                .hasMessageContaining("Coca-Cola 500ml")
                .hasMessageContaining("3");
    }

    @Test
    void createSaleDoesNotSaveWhenValidationFails() throws SQLException {
        when(inventoryService.validateStock(1L, 5)).thenReturn(false);
        when(inventoryService.getAvailableStock(1L)).thenReturn(3);
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Sprite 500ml")));

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 5, 550.0);

        try {
            salesService.createSale(sale, Collections.singletonList(item));
        } catch (SalesService.ValidationException e) {
            // expected
        }

        verify(saleRepository, never()).saveWithItems(any(Connection.class), any(), any());
    }

    // --- cancelSale tests ---
    // PR3 (REQ-CANCEL-01): the sale re-read and the CANCELLED status check now
    // happen INSIDE the transaction using the transactional connection; the
    // idempotent second attempt throws IllegalStateException("Sale already cancelled").

    @Test
    @DisplayName("cancelSale revierte stock con ENTRY y actualiza status (relectura transaccional)")
    void cancelSaleCreatesEntryMovementsAndUpdatesStatus() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        sale.setId(1L);
        SaleItem item = createSaleItem(1L, 2, 600.0);

        when(saleRepository.findById(connection, 1L)).thenReturn(Optional.of(sale));
        when(saleRepository.findItemsBySaleId(connection, 1L)).thenReturn(Collections.singletonList(item));
        when(connection.getAutoCommit()).thenReturn(true);

        salesService.cancelSale(1L, "Cliente devolvió producto");

        verify(saleRepository).findById(connection, 1L);
        verify(saleRepository).findItemsBySaleId(connection, 1L);
        verify(stockMovementRepository).insert(eq(connection), argThat(movement ->
                "ENTRY".equals(movement.getMovementType()) &&
                "SALE".equals(movement.getReferenceType()) &&
                movement.getReferenceId() == 1L &&
                movement.getQuantity() == 2
        ));
        verify(saleRepository).updateStatus(eq(connection), eq(1L), eq("CANCELLED"), anyString(), eq("Cliente devolvió producto"));
        verify(connection).commit();
    }

    @Test
    @DisplayName("cancelSale lanza error si venta no existe")
    void cancelSaleThrowsWhenSaleNotFound() throws SQLException {
        when(saleRepository.findById(connection, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salesService.cancelSale(999L, "Motivo test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Venta no encontrada");

        verify(connection).rollback();
    }

    @Test
    @DisplayName("cancelSale lanza IllegalStateException si venta ya está cancelada")
    void cancelSaleThrowsWhenAlreadyCancelled() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        sale.setId(1L);
        sale.setStatus("CANCELLED");

        when(saleRepository.findById(connection, 1L)).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> salesService.cancelSale(1L, "Motivo test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Sale already cancelled");

        verify(connection).rollback();
        verify(stockMovementRepository, never()).insert(any(Connection.class), any(StockMovement.class));
    }

    @Test
    @DisplayName("cancelSale cancela con motivo vacío correctamente")
    void cancelSaleWithEmptyReason() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        sale.setId(1L);
        SaleItem item = createSaleItem(1L, 2, 600.0);

        when(saleRepository.findById(connection, 1L)).thenReturn(Optional.of(sale));
        when(saleRepository.findItemsBySaleId(connection, 1L)).thenReturn(Collections.singletonList(item));
        when(connection.getAutoCommit()).thenReturn(true);

        salesService.cancelSale(1L, "");

        verify(saleRepository).updateStatus(eq(connection), eq(1L), eq("CANCELLED"), anyString(), eq(""));
        verify(connection).commit();
    }

    @Test
    @DisplayName("cancelSale hace rollback si SQLException ocurre")
    void cancelSaleRollbackOnError() throws SQLException {
        Sale sale = createSale("IN", "CASH");
        sale.setId(1L);
        SaleItem item = createSaleItem(1L, 2, 600.0);

        when(saleRepository.findById(connection, 1L)).thenReturn(Optional.of(sale));
        when(saleRepository.findItemsBySaleId(connection, 1L)).thenReturn(Collections.singletonList(item));
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(new SQLException("DB error")).when(stockMovementRepository).insert(eq(connection), any(StockMovement.class));

        assertThatThrownBy(() -> salesService.cancelSale(1L, "Motivo"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al anular venta");

        verify(connection).rollback();
    }

    // --- generateReceipt tests ---

    @Test
    void generateReceiptContainsStoreName() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Coca-Cola 500ml")));

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 2, 600.0);
        item.setSubtotal(1200.0);

        String receipt = salesService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Mi negocio");
    }

    @Test
    void generateReceiptContainsItemsAndTotal() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Coca-Cola 500ml")));

        Sale sale = createSale("IN", "CASH");
        sale.setTotalAmount(1200.0);
        SaleItem item = createSaleItem(1L, 2, 600.0);
        item.setSubtotal(1200.0);

        String receipt = salesService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Coca-Cola 500ml");
        assertThat(receipt).contains("x2");
        assertThat(receipt).contains("TOTAL");
    }

    @Test
    void generateReceiptContainsChannelAndPayment() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Pepsi 500ml")));

        Sale sale = createSale("PEDIDOSYA", "DEBIT_CARD");
        SaleItem item = createSaleItem(1L, 1, 550.0);
        item.setSubtotal(550.0);

        String receipt = salesService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("PedidosYa");
        assertThat(receipt).contains("Tarjeta de Débito");
    }

    @Test
    void generateReceiptFormatsCurrencyInArgentineStyle() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Agua 500ml")));

        Sale sale = createSale("IN", "CASH");
        sale.setTotalAmount(1500.0);
        SaleItem item = createSaleItem(1L, 3, 500.0);
        item.setSubtotal(1500.0);

        String receipt = salesService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("1.500");
    }

    @Test
    @DisplayName("createSale lanza RuntimeException cuando SQLException en repositorio")
    void createSale_whenSqlException_throwsRuntimeException() throws SQLException {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        when(saleRepository.saveWithItems(any(Connection.class), any(Sale.class), anyList())).thenThrow(new SQLException("DB error"));

        Sale sale = createSale("IN", "CASH");
        SaleItem item = createSaleItem(1L, 2, 600.0);

        assertThatThrownBy(() -> salesService.createSale(sale, Collections.singletonList(item)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al procesar venta");
    }

    @Test
    @DisplayName("generateReceipt muestra descuento por item cuando corresponde")
    void generateReceipt_withItemDiscount() throws SQLException {
        when(productRepository.findById(1L)).thenReturn(Optional.of(createProduct("Coca-Cola 500ml")));

        Sale sale = createSale("IN", "CASH");
        sale.setTotalAmount(1080.0);
        SaleItem item = createSaleItem(1L, 2, 600.0);
        item.setSubtotal(1200.0);
        item.setDiscount(10.0);
        item.setDiscountType("PERCENTAGE");

        String receipt = salesService.generateReceipt(sale, Collections.singletonList(item));

        assertThat(receipt).contains("Descuento: 10%");
    }

    @Test
    @DisplayName("createSale loguea y devuelve true cuando falla el guardado del comprobante (best-effort)")
    void createSale_whenReceiptSaveFails_logsAndStillReturnsTrue() throws SQLException {
        when(inventoryService.validateStock(1L, 2)).thenReturn(true);
        when(saleRepository.saveWithItems(any(Connection.class), any(Sale.class), anyList())).thenReturn(1L);
        doThrow(new RuntimeException("receipt boom")).when(saleRepository).updateReceipt(any(Connection.class), eq(1L), anyString());

        Logger logger = Logger.getLogger(SalesService.class.getName());
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) { records.add(record); }
            @Override
            public void flush() { }
            @Override
            public void close() { }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);

        try {
            Sale sale = createSale("IN", "CASH");
            SaleItem item = createSaleItem(1L, 2, 600.0);

            boolean result = salesService.createSale(sale, Collections.singletonList(item));

            assertThat(result).isTrue();
            assertThat(records)
                    .anyMatch(r -> r.getLevel() == Level.WARNING
                            && r.getMessage().contains("receipt")
                            && r.getThrown() instanceof RuntimeException);
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }

    private Sale createSale(String channel, String paymentMethod) {
        Sale sale = new Sale();
        sale.setSaleDate("2026-07-22");
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

    private Product createProduct(String name) {
        Product product = new Product();
        product.setId(1L);
        product.setName(name);
        product.setSalePrice(600.0);
        return product;
    }
}
