package com.softwaredebebidas.service;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.StockMovement;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ProductRepository productRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(stockMovementRepository, productRepository);
    }

    @Test
    void getCurrentStockDelegatesToRepository() throws SQLException {
        when(stockMovementRepository.computeCurrentStock(1L)).thenReturn(24);

        int stock = inventoryService.getCurrentStock(1L);

        assertThat(stock).isEqualTo(24);
    }

    @Test
    void getStockStatusReturnsOKWhenAboveMinStock() throws SQLException {
        Product product = new Product();
        product.setId(1L);
        product.setMinStock(10);
        when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(stockMovementRepository.computeCurrentStock(1L)).thenReturn(24);

        String status = inventoryService.getStockStatus(1L);

        assertThat(status).isEqualTo("OK");
    }

    @Test
    void getStockStatusReturnsLOWWhenAtMinStock() throws SQLException {
        Product product = new Product();
        product.setId(1L);
        product.setMinStock(10);
        when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(stockMovementRepository.computeCurrentStock(1L)).thenReturn(10);

        String status = inventoryService.getStockStatus(1L);

        assertThat(status).isEqualTo("LOW");
    }

    @Test
    void getStockStatusReturnsOUTWhenZero() throws SQLException {
        Product product = new Product();
        product.setId(1L);
        product.setMinStock(10);
        when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(stockMovementRepository.computeCurrentStock(1L)).thenReturn(0);

        String status = inventoryService.getStockStatus(1L);

        assertThat(status).isEqualTo("OUT");
    }

    @Test
    void getLowStockProductsReturnsProductsWithStockAtOrBelowMin() throws SQLException {
        Product p1 = new Product();
        p1.setId(1L);
        p1.setName("Product A");
        p1.setMinStock(10);

        Product p2 = new Product();
        p2.setId(2L);
        p2.setName("Product B");
        p2.setMinStock(5);

        Product p3 = new Product();
        p3.setId(3L);
        p3.setName("Product C");
        p3.setMinStock(0);

        when(productRepository.findAllActive()).thenReturn(Arrays.asList(p1, p2, p3));
        when(stockMovementRepository.computeCurrentStocks(Arrays.asList(1L, 2L, 3L)))
                .thenReturn(java.util.Map.of(1L, 5, 2L, 0, 3L, 20)); // LOW, OUT, OK

        List<Product> lowStock = inventoryService.getLowStockProducts();

        assertThat(lowStock).hasSize(2);
        assertThat(lowStock).extracting(Product::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    // --- Stock validation tests ---

    @Test
    void validateStockReturnsTrueWhenSufficientStock() throws SQLException {
        when(stockMovementRepository.computeCurrentStock(1L)).thenReturn(10);

        boolean valid = inventoryService.validateStock(1L, 5);

        assertThat(valid).isTrue();
    }

    @Test
    void validateStockReturnsFalseWhenInsufficientStock() throws SQLException {
        when(stockMovementRepository.computeCurrentStock(1L)).thenReturn(3);

        boolean valid = inventoryService.validateStock(1L, 5);

        assertThat(valid).isFalse();
    }

    @Test
    void validateStockReturnsTrueWhenExactStock() throws SQLException {
        when(stockMovementRepository.computeCurrentStock(1L)).thenReturn(5);

        boolean valid = inventoryService.validateStock(1L, 5);

        assertThat(valid).isTrue();
    }

    @Test
    void getAvailableStockDelegatesToRepository() throws SQLException {
        when(stockMovementRepository.computeCurrentStock(1L)).thenReturn(24);

        int stock = inventoryService.getAvailableStock(1L);

        assertThat(stock).isEqualTo(24);
    }

    // --- Expiry validation tests ---

    @Test
    void isExpiredReturnsTrueForPastDate() {
        boolean result = inventoryService.isExpired("01/01/2025");

        assertThat(result).isTrue();
    }

    @Test
    void isExpiredReturnsFalseForFutureDate() {
        boolean result = inventoryService.isExpired("31/12/2027");

        assertThat(result).isFalse();
    }

    @Test
    void isExpiredReturnsFalseForNullDate() {
        boolean result = inventoryService.isExpired(null);

        assertThat(result).isFalse();
    }

    @Test
    void isExpiredReturnsFalseForEmptyDate() {
        boolean result = inventoryService.isExpired("");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isExpired retorna false para la fecha de hoy (vencimiento es hoy, no pasó)")
    void isExpiredReturnsFalseForToday() {
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        boolean result = inventoryService.isExpired(today);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isExpired retorna false para fecha inválida")
    void isExpiredReturnsFalseForInvalidFormat() {
        boolean result = inventoryService.isExpired("not-a-date");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isExpired loguea FINE y sigue devolviendo false ante fecha inválida")
    void isExpired_logsAtFineLevelForInvalidFormat() {
        Logger logger = Logger.getLogger(InventoryService.class.getName());
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
            boolean result = inventoryService.isExpired("not-a-date");

            assertThat(result).isFalse();
            assertThat(records)
                    .anyMatch(r -> r.getLevel() == Level.FINE
                            && r.getMessage().contains("not-a-date"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    @DisplayName("isExpiringSoon loguea FINE y sigue devolviendo false ante fecha inválida")
    void isExpiringSoon_logsAtFineLevelForInvalidFormat() {
        Logger logger = Logger.getLogger(InventoryService.class.getName());
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
            boolean result = inventoryService.isExpiringSoon("not-a-date");

            assertThat(result).isFalse();
            assertThat(records)
                    .anyMatch(r -> r.getLevel() == Level.FINE
                            && r.getMessage().contains("not-a-date"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void isExpiringSoonReturnsTrueForDateWithin7Days() {
        // Use a date that's 3 days from now
        java.time.LocalDate nearFuture = java.time.LocalDate.now().plusDays(3);
        String dateStr = nearFuture.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        boolean result = inventoryService.isExpiringSoon(dateStr);

        assertThat(result).isTrue();
    }

    @Test
    void isExpiringSoonReturnsFalseForDateBeyond7Days() {
        java.time.LocalDate farFuture = java.time.LocalDate.now().plusDays(10);
        String dateStr = farFuture.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        boolean result = inventoryService.isExpiringSoon(dateStr);

        assertThat(result).isFalse();
    }

    @Test
    void isExpiringSoonReturnsFalseForExpiredDate() {
        boolean result = inventoryService.isExpiringSoon("01/01/2025");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isExpiringSoon retorna true exactamente a los 7 días (borde inclusivo)")
    void isExpiringSoonReturnsTrueForExactly7Days() {
        String dateStr = java.time.LocalDate.now().plusDays(7)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        boolean result = inventoryService.isExpiringSoon(dateStr);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isExpiringSoon retorna true para la fecha de hoy")
    void isExpiringSoonReturnsTrueForToday() {
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        boolean result = inventoryService.isExpiringSoon(today);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isExpiringSoon retorna false a los 8 días (fuera de ventana)")
    void isExpiringSoonReturnsFalseFor8Days() {
        String dateStr = java.time.LocalDate.now().plusDays(8)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        boolean result = inventoryService.isExpiringSoon(dateStr);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isExpiringSoon retorna false para fecha inválida")
    void isExpiringSoonReturnsFalseForInvalidFormat() {
        boolean result = inventoryService.isExpiringSoon("not-a-date");

        assertThat(result).isFalse();
    }

    @Test
    void needsExpiryAlertReturnsTrueForExpiredProduct() {
        boolean result = inventoryService.needsExpiryAlert("01/01/2025");

        assertThat(result).isTrue();
    }

    @Test
    void needsExpiryAlertReturnsTrueForExpiringSoonProduct() {
        java.time.LocalDate nearFuture = java.time.LocalDate.now().plusDays(5);
        String dateStr = nearFuture.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        boolean result = inventoryService.needsExpiryAlert(dateStr);

        assertThat(result).isTrue();
    }

    @Test
    void needsExpiryAlertReturnsFalseForHealthyProduct() {
        java.time.LocalDate farFuture = java.time.LocalDate.now().plusDays(30);
        String dateStr = farFuture.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        boolean result = inventoryService.needsExpiryAlert(dateStr);

        assertThat(result).isFalse();
    }

    // --- adjustStock tests ---

    @Test
    void adjustStockPositiveCreatesAdjustmentMovement() throws SQLException {
        Connection conn = mock(Connection.class);
        when(stockMovementRepository.getConnection()).thenReturn(conn);
        when(conn.getAutoCommit()).thenReturn(true);
        when(stockMovementRepository.insert(eq(conn), any(StockMovement.class))).thenReturn(1L);

        inventoryService.adjustStock(1L, 10, "Reposición de inventario");

        verify(stockMovementRepository).insert(eq(conn), argThat(m ->
                "ADJUSTMENT".equals(m.getMovementType()) &&
                m.getQuantity() == 10 &&
                "ADJUSTMENT".equals(m.getReferenceType()) &&
                "Reposición de inventario".equals(m.getNotes())
        ));
        verify(conn).commit();
    }

    @Test
    void adjustStockNegativeCreatesExitMovement() throws SQLException {
        Connection conn = mock(Connection.class);
        when(stockMovementRepository.getConnection()).thenReturn(conn);
        when(conn.getAutoCommit()).thenReturn(true);
        when(stockMovementRepository.computeCurrentStock(conn, 1L)).thenReturn(10);

        inventoryService.adjustStock(1L, -5, "Producto dañado");

        verify(stockMovementRepository).insert(eq(conn), argThat(m ->
                "EXIT".equals(m.getMovementType()) &&
                m.getQuantity() == 5 &&
                "ADJUSTMENT".equals(m.getReferenceType()) &&
                "Producto dañado".equals(m.getNotes())
        ));
        verify(conn).setAutoCommit(false);
        verify(conn).commit();
        verify(conn).setAutoCommit(true); // restore original value
    }

    @Test
    void adjustStockNegativeThrowsWhenRemovingMoreThanStock() throws SQLException {
        Connection conn = mock(Connection.class);
        when(stockMovementRepository.getConnection()).thenReturn(conn);
        when(conn.getAutoCommit()).thenReturn(true);
        when(stockMovementRepository.computeCurrentStock(conn, 1L)).thenReturn(3);

        assertThatThrownBy(() -> inventoryService.adjustStock(1L, -5, "Sin stock"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stock insuficiente");

        verify(stockMovementRepository, never()).insert(eq(conn), any(StockMovement.class));
        verify(conn, never()).commit();
        verify(conn).setAutoCommit(true); // autoCommit restored by finally
    }

    @Test
    void adjustStockNegativeRollsBackOnSqlException() throws SQLException {
        Connection conn = mock(Connection.class);
        when(stockMovementRepository.getConnection()).thenReturn(conn);
        when(conn.getAutoCommit()).thenReturn(true);
        when(stockMovementRepository.computeCurrentStock(conn, 1L)).thenReturn(10);
        when(stockMovementRepository.insert(eq(conn), any(StockMovement.class)))
                .thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> inventoryService.adjustStock(1L, -5, "Falla"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al registrar ajuste");

        verify(conn).rollback();
        verify(conn, never()).commit();
        verify(conn).setAutoCommit(true);
    }

    @Test
    void adjustStockZeroCreatesAdjustmentWithZeroQuantity() throws SQLException {
        Connection conn = mock(Connection.class);
        when(stockMovementRepository.getConnection()).thenReturn(conn);
        when(conn.getAutoCommit()).thenReturn(true);
        when(stockMovementRepository.insert(eq(conn), any(StockMovement.class))).thenReturn(1L);

        inventoryService.adjustStock(1L, 0, "Sin cambios");

        verify(stockMovementRepository).insert(eq(conn), argThat(m ->
                "ADJUSTMENT".equals(m.getMovementType()) &&
                m.getQuantity() == 0
        ));
    }

    // --- Regression tests: current_stock denormalized column must stay in sync (audit v3, B2) ---

    @Test
    @DisplayName("REGRESION B2: ajuste positivo debe actualizar current_stock (falla hoy: adjustStock nunca llama updateStock)")
    void adjustStockPositive_shouldUpdateCurrentStock() throws SQLException {
        Connection conn = mock(Connection.class);
        when(stockMovementRepository.getConnection()).thenReturn(conn);
        when(conn.getAutoCommit()).thenReturn(true);
        when(stockMovementRepository.insert(eq(conn), any(StockMovement.class))).thenReturn(1L);

        inventoryService.adjustStock(1L, 10, "Reposición de inventario");

        verify(productRepository).updateStock(eq(conn), eq(1L), eq(10));
    }

    @Test
    @DisplayName("REGRESION B2: ajuste negativo debe actualizar current_stock (falla hoy: adjustStock nunca llama updateStock)")
    void adjustStockNegative_shouldUpdateCurrentStock() throws SQLException {
        Connection conn = mock(Connection.class);
        when(stockMovementRepository.getConnection()).thenReturn(conn);
        when(conn.getAutoCommit()).thenReturn(true);
        when(stockMovementRepository.computeCurrentStock(conn, 1L)).thenReturn(10);

        inventoryService.adjustStock(1L, -5, "Producto dañado");

        verify(productRepository).updateStock(eq(conn), eq(1L), eq(-5));
    }

    // --- getMovementHistory (with filters) tests ---

    @Test
    void getMovementHistoryWithFiltersDelegatesToRepository() throws SQLException {
        List<StockMovement> movements = Arrays.asList(new StockMovement(), new StockMovement());
        when(stockMovementRepository.findByFilters(1L, "ENTRY", null, null)).thenReturn(movements);

        List<StockMovement> result = inventoryService.getMovementHistory(1L, "ENTRY", null, null);

        assertThat(result).hasSize(2);
        verify(stockMovementRepository).findByFilters(1L, "ENTRY", null, null);
    }

    @Test
    void getMovementHistoryWithAllFiltersDelegatesCorrectly() throws SQLException {
        when(stockMovementRepository.findByFilters(1L, "EXIT", "01/01/2025", "31/12/2025")).thenReturn(Collections.emptyList());

        List<StockMovement> result = inventoryService.getMovementHistory(1L, "EXIT", "01/01/2025", "31/12/2025");

        assertThat(result).isEmpty();
        verify(stockMovementRepository).findByFilters(1L, "EXIT", "01/01/2025", "31/12/2025");
    }

    // ========================================
    // SQLException error paths
    // ========================================

    @Test
    @DisplayName("getCurrentStock lanza RuntimeException cuando SQLException en repositorio")
    void getCurrentStock_whenSqlException_throwsRuntimeException() throws SQLException {
        when(stockMovementRepository.computeCurrentStock(1L)).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> inventoryService.getCurrentStock(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al calcular stock");
    }

    @Test
    @DisplayName("getStockStatus lanza RuntimeException cuando SQLException en repositorio")
    void getStockStatus_whenSqlException_throwsRuntimeException() throws SQLException {
        when(stockMovementRepository.computeCurrentStock(1L)).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> inventoryService.getStockStatus(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al obtener estado de stock");
    }

    @Test
    @DisplayName("adjustStock lanza RuntimeException cuando SQLException en repositorio")
    void adjustStock_whenSqlException_throwsRuntimeException() throws SQLException {
        Connection conn = mock(Connection.class);
        when(stockMovementRepository.getConnection()).thenReturn(conn);
        when(conn.getAutoCommit()).thenReturn(true);
        when(stockMovementRepository.insert(eq(conn), any(StockMovement.class))).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> inventoryService.adjustStock(1L, 5, "Test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al registrar ajuste");

        verify(conn).rollback();
    }

    // ========================================
    // Edge cases
    // ========================================

    @Test
    @DisplayName("getStockStatus retorna OUT cuando stock es negativo")
    void getStockStatus_whenStockNegative_returnsOut() throws SQLException {
        Product product = new Product();
        product.setId(1L);
        product.setMinStock(10);
        when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(product));
        when(stockMovementRepository.computeCurrentStock(1L)).thenReturn(-3);

        String status = inventoryService.getStockStatus(1L);

        assertThat(status).isEqualTo("OUT");
    }

    @Test
    @DisplayName("getStockStatus retorna OUT cuando producto no existe")
    void getStockStatus_whenProductNotFound_returnsOut() throws SQLException {
        when(stockMovementRepository.computeCurrentStock(1L)).thenReturn(0);
        when(productRepository.findById(1L)).thenReturn(java.util.Optional.empty());

        String status = inventoryService.getStockStatus(1L);

        assertThat(status).isEqualTo("OUT");
    }

    @Test
    @DisplayName("getLowStockProducts retorna lista vacía cuando ningún producto está bajo mínimo")
    void getLowStockProducts_whenNoneLow_returnsEmpty() throws SQLException {
        Product product = new Product();
        product.setId(1L);
        product.setName("Producto OK");
        product.setMinStock(5);
        when(productRepository.findAllActive()).thenReturn(Collections.singletonList(product));
        when(stockMovementRepository.computeCurrentStocks(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonMap(1L, 20));

        List<Product> result = inventoryService.getLowStockProducts();

        assertThat(result).isEmpty();
    }
}
