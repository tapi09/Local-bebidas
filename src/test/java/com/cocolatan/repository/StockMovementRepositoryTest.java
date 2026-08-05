package com.cocolatan.repository;

import com.cocolatan.model.StockMovement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

class StockMovementRepositoryTest {

    private DatabaseManager dbManager;
    private StockMovementRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = DatabaseManager.createInMemory();
        repository = new StockMovementRepository(dbManager);

        // Insert a product for foreign key
        dbManager.getConnection().createStatement().execute(
                "INSERT INTO products (name, category, presentation, cost_price, sale_price) "
                        + "VALUES ('Coca-Cola 500ml', 'Gaseosa', 'Botella', 350, 600)"
        );
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void insertReturnsId() throws SQLException {
        StockMovement movement = createMovement(1L, "ENTRY", 24);

        Long id = repository.insert(movement);

        assertThat(id).isNotNull();
        assertThat(id).isGreaterThan(0);
    }

    @Test
    void findByProductIdReturnsMovements() throws SQLException {
        repository.insert(createMovement(1L, "ENTRY", 24));
        repository.insert(createMovement(1L, "EXIT", 2));
        repository.insert(createMovement(1L, "ENTRY", 10));

        List<StockMovement> movements = repository.findByProductId(1L);

        assertThat(movements).hasSize(3);
    }

    @Test
    void findByProductIdReturnsEmptyForNoMovements() throws SQLException {
        List<StockMovement> movements = repository.findByProductId(999L);
        assertThat(movements).isEmpty();
    }

    @Test
    void computeCurrentStockWithEntryOnly() throws SQLException {
        repository.insert(createMovement(1L, "ENTRY", 24));

        int stock = repository.computeCurrentStock(1L);

        assertThat(stock).isEqualTo(24);
    }

    @Test
    void computeCurrentStockWithEntryAndExit() throws SQLException {
        repository.insert(createMovement(1L, "ENTRY", 24));
        repository.insert(createMovement(1L, "EXIT", 2));
        repository.insert(createMovement(1L, "EXIT", 6));

        int stock = repository.computeCurrentStock(1L);

        assertThat(stock).isEqualTo(16);
    }

    @Test
    void computeCurrentStockWithAdjustment() throws SQLException {
        repository.insert(createMovement(1L, "ENTRY", 10));
        repository.insert(createMovement(1L, "EXIT", 3));
        repository.insert(createMovement(1L, "ADJUSTMENT", 2)); // ADJUSTMENT adds (positive)

        int stock = repository.computeCurrentStock(1L);

        assertThat(stock).isEqualTo(9);
    }

    @Test
    void computeCurrentStockReturnsZeroForNoMovements() throws SQLException {
        int stock = repository.computeCurrentStock(999L);
        assertThat(stock).isEqualTo(0);
    }

    @Test
    @DisplayName("computeCurrentStock retorna negativo cuando EXIT supera ENTRY")
    void computeCurrentStockWithMoreExitThanEntryIsNegative() throws SQLException {
        repository.insert(createMovement(1L, "ENTRY", 5));
        repository.insert(createMovement(1L, "EXIT", 8));

        int stock = repository.computeCurrentStock(1L);

        assertThat(stock).isEqualTo(-3);
    }

    @Test
    void findByProductIdAndTypeFiltersCorrectly() throws SQLException {
        repository.insert(createMovement(1L, "ENTRY", 24));
        repository.insert(createMovement(1L, "EXIT", 2));
        repository.insert(createMovement(1L, "ENTRY", 10));

        List<StockMovement> entries = repository.findByProductIdAndType(1L, "ENTRY");

        assertThat(entries).hasSize(2);
        assertThat(entries).allMatch(m -> "ENTRY".equals(m.getMovementType()));
    }

    // --- findByFilters tests ---

    @Test
    void findByFiltersReturnsAllWhenNoFilters() throws SQLException {
        repository.insert(createMovement(1L, "ENTRY", 24));
        repository.insert(createMovement(1L, "EXIT", 2));

        List<StockMovement> results = repository.findByFilters(null, null, null, null);

        assertThat(results).hasSize(2);
    }

    @Test
    void findByFiltersByProductId() throws SQLException {
        // Insert product 2
        dbManager.getConnection().createStatement().execute(
                "INSERT INTO products (name, category, presentation, cost_price, sale_price) "
                        + "VALUES ('Pepsi 500ml', 'Gaseosa', 'Botella', 350, 600)"
        );

        repository.insert(createMovement(1L, "ENTRY", 24));
        repository.insert(createMovement(2L, "ENTRY", 10));

        List<StockMovement> results = repository.findByFilters(1L, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProductId()).isEqualTo(1L);
    }

    @Test
    void findByFiltersByType() throws SQLException {
        repository.insert(createMovement(1L, "ENTRY", 24));
        repository.insert(createMovement(1L, "EXIT", 2));
        repository.insert(createMovement(1L, "ADJUSTMENT", 5));

        List<StockMovement> results = repository.findByFilters(null, "EXIT", null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMovementType()).isEqualTo("EXIT");
    }

    @Test
    void findByFiltersByProductAndType() throws SQLException {
        repository.insert(createMovement(1L, "ENTRY", 24));
        repository.insert(createMovement(1L, "EXIT", 2));
        repository.insert(createMovement(1L, "ADJUSTMENT", 5));

        List<StockMovement> results = repository.findByFilters(1L, "ADJUSTMENT", null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMovementType()).isEqualTo("ADJUSTMENT");
    }

    @Test
    void findByFiltersWithEmptyTypeReturnsAll() throws SQLException {
        repository.insert(createMovement(1L, "ENTRY", 24));
        repository.insert(createMovement(1L, "EXIT", 2));

        List<StockMovement> results = repository.findByFilters(1L, "", null, null);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("findByFilters() combina todos los filtros simultáneamente")
    void findByFiltersWithAllFilters() throws SQLException {
        dbManager.getConnection().createStatement().execute(
                "INSERT INTO products (name, category, presentation, cost_price, sale_price) "
                        + "VALUES ('Pepsi 500ml', 'Gaseosa', 'Botella', 350, 600)"
        );
        dbManager.getConnection().createStatement().execute(
                "INSERT INTO stock_movements (product_id, movement_type, quantity, created_at) "
                        + "VALUES (1, 'ENTRY', 10, '2026-01-15 10:00:00')"
        );
        dbManager.getConnection().createStatement().execute(
                "INSERT INTO stock_movements (product_id, movement_type, quantity, created_at) "
                        + "VALUES (1, 'ENTRY', 5, '2026-02-20 10:00:00')"
        );
        dbManager.getConnection().createStatement().execute(
                "INSERT INTO stock_movements (product_id, movement_type, quantity, created_at) "
                        + "VALUES (2, 'ENTRY', 20, '2026-01-15 10:00:00')"
        );
        dbManager.getConnection().createStatement().execute(
                "INSERT INTO stock_movements (product_id, movement_type, quantity, created_at) "
                        + "VALUES (1, 'EXIT', 3, '2026-03-10 10:00:00')"
        );

        List<StockMovement> results = repository.findByFilters(1L, "ENTRY", "01/01/2026", "01/02/2026");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProductId()).isEqualTo(1L);
        assertThat(results.get(0).getMovementType()).isEqualTo("ENTRY");
        assertThat(results.get(0).getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("computeCurrentStocks() batch para múltiples productos")
    void computeCurrentStocksBatch() throws SQLException {
        dbManager.getConnection().createStatement().execute(
                "INSERT INTO products (name, category, presentation, cost_price, sale_price) "
                        + "VALUES ('Pepsi 500ml', 'Gaseosa', 'Botella', 350, 600)"
        );
        repository.insert(createMovement(1L, "ENTRY", 24));
        repository.insert(createMovement(1L, "EXIT", 4));
        repository.insert(createMovement(2L, "ENTRY", 50));
        repository.insert(createMovement(1L, "ENTRY", 6));

        java.util.Map<Long, Integer> stocks = repository.computeCurrentStocks(java.util.List.of(1L, 2L));

        assertThat(stocks).hasSize(2);
        assertThat(stocks.get(1L)).isEqualTo(26);
        assertThat(stocks.get(2L)).isEqualTo(50);
    }

    @Test
    @DisplayName("computeCurrentStocks() con lista vacía retorna mapa vacío")
    void computeCurrentStocksWithEmptyList() throws SQLException {
        java.util.Map<Long, Integer> stocks = repository.computeCurrentStocks(java.util.List.of());

        assertThat(stocks).isEmpty();
    }

    private StockMovement createMovement(Long productId, String type, int quantity) {
        StockMovement movement = new StockMovement();
        movement.setProductId(productId);
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setReferenceType("PURCHASE");
        movement.setReferenceId(1L);
        movement.setNotes("Test movement");
        return movement;
    }
}
