package com.cocolatan.repository;

import com.cocolatan.model.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductRepositoryTest {

    private DatabaseManager dbManager;
    private ProductRepository repository;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
        repository = new ProductRepository(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void saveReturnsId() throws SQLException {
        Product product = createProduct("Coca-Cola 500ml", "Gaseosa");
        Long id = repository.save(product);
        assertThat(id).isNotNull();
        assertThat(id).isGreaterThan(0);
    }

    @Test
    void saveWritesBackGeneratedId() throws SQLException {
        Product product = createProduct("Coca-Cola 500ml", "Gaseosa");

        Long id = repository.save(product);

        assertThat(product.getId()).isNotNull();
        assertThat(product.getId()).isEqualTo(id);
    }

    @Test
    void findByIdReturnsSavedProduct() throws SQLException {
        Product product = createProduct("Coca-Cola 500ml", "Gaseosa");
        Long id = repository.save(product);

        Optional<Product> found = repository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Coca-Cola 500ml");
        assertThat(found.get().getCategory()).isEqualTo("Gaseosa");
    }

    @Test
    void findByIdReturnsEmptyForNonexistent() throws SQLException {
        Optional<Product> found = repository.findById(999L);
        assertThat(found).isEmpty();
    }

    @Test
    void findAllActiveReturnsOnlyActiveProducts() throws SQLException {
        repository.save(createProduct("Coca-Cola 500ml", "Gaseosa"));
        repository.save(createProduct("Pepsi 500ml", "Gaseosa"));
        Product inactive = createProduct("Inactive Product", "Gaseosa");
        Long inactiveId = repository.save(inactive);
        repository.deactivate(inactiveId);

        List<Product> products = repository.findAllActive();

        assertThat(products).hasSize(2);
    }

    @Test
    void searchByNameFindsMatchingProducts() throws SQLException {
        repository.save(createProduct("Coca-Cola 500ml", "Gaseosa"));
        repository.save(createProduct("Pepsi 500ml", "Gaseosa"));
        repository.save(createProduct("Agua Villavicencio", "Agua"));

        List<Product> results = repository.searchByName("coca");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Coca-Cola 500ml");
    }

    @Test
    void searchByNameIsCaseInsensitive() throws SQLException {
        repository.save(createProduct("Coca-Cola 500ml", "Gaseosa"));

        List<Product> results = repository.searchByName("COCA");

        assertThat(results).hasSize(1);
    }

    @Test
    void searchByBarcodeFindsExactMatch() throws SQLException {
        Product product = createProduct("Coca-Cola 500ml", "Gaseosa");
        product.setBarcode("7790001234567");
        repository.save(product);

        List<Product> results = repository.searchByBarcode("7790001234567");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBarcode()).isEqualTo("7790001234567");
    }

    @Test
    void findByCategoryFiltersCorrectly() throws SQLException {
        repository.save(createProduct("Coca-Cola 500ml", "Gaseosa"));
        repository.save(createProduct("Pepsi 500ml", "Gaseosa"));
        repository.save(createProduct("Agua Villavicencio", "Agua"));

        List<Product> results = repository.findByCategory("Agua");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Agua Villavicencio");
    }

    @Test
    void saveRejectsDuplicateBarcode() throws SQLException {
        Product p1 = createProduct("Coca-Cola 500ml", "Gaseosa");
        p1.setBarcode("7790001234567");
        repository.save(p1);

        Product p2 = createProduct("Coca-Cola 1L", "Gaseosa");
        p2.setBarcode("7790001234567");

        assertThatThrownBy(() -> repository.save(p2))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void findDistinctCategoriesExcludesBlankAndInactive() throws SQLException {
        repository.save(createProduct("Coca-Cola 500ml", "Gaseosa"));
        repository.save(createProduct("Quilmes 1L", "Cerveza"));
        Product uncategorized = createProduct("Bolsita", "");
        repository.save(uncategorized);
        Product inactive = createProduct("Agua Villavicencio", "Agua");
        Long inactiveId = repository.save(inactive);
        repository.deactivate(inactiveId);

        List<String> categories = repository.findDistinctCategories();

        assertThat(categories).containsExactly("Cerveza", "Gaseosa");
    }

    @Test
    void findDistinctCategoriesCaseInsensitiveOrder() throws SQLException {
        repository.save(createProduct("Quilmes 1L", "cerveza"));
        repository.save(createProduct("Agua Villavicencio", "Agua"));
        repository.save(createProduct("Coca-Cola 500ml", "Gaseosa"));

        List<String> categories = repository.findDistinctCategories();

        assertThat(categories).containsExactly("Agua", "cerveza", "Gaseosa");
    }

    @Test
    void updateModifiesProduct() throws SQLException {
        Product product = createProduct("Coca-Cola 500ml", "Gaseosa");
        Long id = repository.save(product);

        product.setId(id);
        product.setSalePrice(650.0);
        repository.update(product);

        Optional<Product> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getSalePrice()).isEqualTo(650.0);
    }

    @Test
    void deactivateSetsActiveFalse() throws SQLException {
        Product product = createProduct("Coca-Cola 500ml", "Gaseosa");
        Long id = repository.save(product);

        repository.deactivate(id);

        Optional<Product> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().isActive()).isFalse();
    }

    @Test
    void hasHistoryReturnsTrueForProductWithPurchases() throws SQLException {
        // Insert a product
        Product product = createProduct("Coca-Cola 500ml", "Gaseosa");
        Long productId = repository.save(product);

        // Insert a supplier first, then a purchase referencing the product
        dbManager.getConnection().createStatement().execute(
                "INSERT INTO suppliers (name) VALUES ('Test Supplier')"
        );
        dbManager.getConnection().createStatement().execute(
                "INSERT INTO purchases (supplier_id, purchase_date, total_amount) VALUES (1, '22/07/2026', 100)"
        );
        dbManager.getConnection().createStatement().execute(
                "INSERT INTO purchase_items (purchase_id, product_id, quantity, unit_cost) VALUES (1, " + productId + ", 10, 100)"
        );

        assertThat(repository.hasHistory(productId)).isTrue();
    }

    @Test
    void hasHistoryReturnsFalseForNewProduct() throws SQLException {
        Product product = createProduct("New Product", "Gaseosa");
        Long id = repository.save(product);

        assertThat(repository.hasHistory(id)).isFalse();
    }

    @Test
    @DisplayName("searchByName() retorna vacío cuando no hay match")
    void searchByNameReturnsEmptyForNoMatch() throws SQLException {
        repository.save(createProduct("Coca-Cola 500ml", "Gaseosa"));

        List<Product> results = repository.searchByName("NOEXISTE");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("updateCostPrice() modifica el precio de costo")
    void updateCostPriceModifiesProduct() throws SQLException {
        Product product = createProduct("Coca-Cola 500ml", "Gaseosa");
        Long id = repository.save(product);

        repository.updateCostPrice(id, 400.0);

        Optional<Product> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getCostPrice()).isEqualTo(400.0);
    }

    @Test
    @DisplayName("searchByName() encuentra producto por código de barras")
    void searchByNameMatchesBarcode() throws SQLException {
        Product p = createProduct("Fernet Branca 750ml", "Aperitivos");
        p.setBarcode("7791234567890");
        repository.save(p);

        List<Product> results = repository.searchByName("7791234567890");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Fernet Branca 750ml");
    }

    @Test
    @DisplayName("searchByName() encuentra producto por SKU")
    void searchByNameMatchesSku() throws SQLException {
        Product p = createProduct("Gancia 950ml", "Aperitivos");
        Long id = repository.save(p);
        String expectedSku = String.format("SKU-%05d", id);

        List<Product> results = repository.searchByName(expectedSku);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Gancia 950ml");
    }

    private long insertCategory(String name) throws SQLException {
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(
                "INSERT INTO categories (name, sort_order, active) VALUES (?, 1, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new SQLException("Failed to retrieve generated category ID");
            }
        }
    }

    private long insertSubcategory(long categoryId, String name) throws SQLException {
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(
                "INSERT INTO subcategories (category_id, name, sort_order, active) VALUES (?, ?, 1, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, categoryId);
            ps.setString(2, name);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new SQLException("Failed to retrieve generated subcategory ID");
            }
        }
    }

    private Product createProduct(String name, String category) {
        Product product = new Product();
        product.setName(name);
        product.setCategoryName(category);
        product.setPresentation("Botella");
        product.setCostPrice(350.0);
        product.setSalePrice(600.0);
        product.setMinStock(10);
        product.setActive(true);
        return product;
    }

    @Test
    void savePersistsCategoryAndSubcategoryIds() throws SQLException {
        long catId = insertCategory("Cervezas Artesanales");
        long subId = insertSubcategory(catId, "Latas");
        Product product = createProduct("Quilmes 1L", "Cervezas Artesanales");
        product.setCategoryId(catId);
        product.setSubcategoryId(subId);

        Long id = repository.save(product);

        Optional<Product> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getCategoryId()).isEqualTo(catId);
        assertThat(found.get().getSubcategoryId()).isEqualTo(subId);
    }

    @Test
    void saveComputesLegacyFromCategoryAndSubcategoryNames() throws SQLException {
        Product product = createProduct("Quilmes 1L", "Cervezas");
        product.setSubcategoryName("Latas");

        Long id = repository.save(product);

        assertThat(repository.findById(id).orElseThrow().getCategory())
                .isEqualTo("Cervezas — Latas");
    }

    @Test
    void saveComputesLegacyFromCategoryNameOnly() throws SQLException {
        Product product = createProduct("Quilmes 1L", "Cervezas");

        Long id = repository.save(product);

        assertThat(repository.findById(id).orElseThrow().getCategory())
                .isEqualTo("Cervezas");
    }

    @Test
    void saveWithoutHierarchyWritesEmptyLegacy() throws SQLException {
        Product product = new Product();
        product.setName("Sueltito");
        product.setPresentation("Unitario");
        product.setCostPrice(50.0);
        product.setSalePrice(100.0);
        product.setMinStock(0);

        Long id = repository.save(product);

        assertThat(repository.findById(id).orElseThrow().getCategory()).isEmpty();
    }

    @Test
    void saveWithoutIdsPersistsNullCategoryColumns() throws SQLException {
        Product product = createProduct("Coca-Cola 500ml", "Gaseosa");

        Long id = repository.save(product);

        Optional<Product> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getCategoryId()).isNull();
        assertThat(found.get().getSubcategoryId()).isNull();
    }

    @Test
    void updatePersistsIdsAndRecomputesLegacy() throws SQLException {
        long catA = insertCategory("Cervezas Artesanales");
        long subA = insertSubcategory(catA, "Latas");
        long catB = insertCategory("Gaseosas Zero");
        long subB = insertSubcategory(catB, "Botella");

        Product product = createProduct("Quilmes 1L", "Cervezas Artesanales");
        product.setSubcategoryName("Latas");
        product.setCategoryId(catA);
        product.setSubcategoryId(subA);
        Long id = repository.save(product);

        product.setCategoryId(catB);
        product.setSubcategoryId(subB);
        product.setCategoryName("Gaseosas Zero");
        product.setSubcategoryName("Botella");
        repository.update(product);

        Optional<Product> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getCategoryId()).isEqualTo(catB);
        assertThat(found.get().getSubcategoryId()).isEqualTo(subB);
        assertThat(found.get().getCategory()).isEqualTo("Gaseosas Zero — Botella");
    }

    @Test
    void findByIdResolvesNamesViaLeftJoin() throws SQLException {
        long catId = insertCategory("Cervezas Artesanales");
        long subId = insertSubcategory(catId, "Latas");
        Product product = createProduct("Quilmes 1L", null);
        product.setCategoryId(catId);
        product.setSubcategoryId(subId);

        Long id = repository.save(product);

        Optional<Product> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getCategoryName()).isEqualTo("Cervezas Artesanales");
        assertThat(found.get().getSubcategoryName()).isEqualTo("Latas");
        assertThat(found.get().getHierarchyLabel()).isEqualTo("Cervezas Artesanales — Latas");
    }

    @Test
    void countLowStockCountsActiveProductsBelowMinStock() throws SQLException {
        Product low1 = createProduct("Cerveza A", "Cervezas");
        Long low1Id = repository.save(low1);
        insertMovement(low1Id, "ENTRY", 8);

        Product healthy = createProduct("Gaseosa B", "Gaseosas");
        Long healthyId = repository.save(healthy);
        insertMovement(healthyId, "ENTRY", 15);

        Product low2 = createProduct("Cerveza C", "Cervezas");
        Long low2Id = repository.save(low2);
        insertMovement(low2Id, "ENTRY", 5);
        insertMovement(low2Id, "ADJUSTMENT", 3);
        insertMovement(low2Id, "EXIT", 2);

        Product equal = createProduct("Gaseosa D", "Gaseosas");
        Long equalId = repository.save(equal);
        insertMovement(equalId, "ENTRY", 10);

        int result = repository.countLowStock();

        assertThat(result).isEqualTo(2);
    }

    @Test
    void countLowStockCountsProductsWithNoMovementsAsLow() throws SQLException {
        Product product = createProduct("Cerveza A", "Cervezas");

        repository.save(product);

        int result = repository.countLowStock();

        assertThat(result).isEqualTo(1);
    }

    @Test
    void countLowStockIgnoresInactiveProducts() throws SQLException {
        Product active = createProduct("Cerveza A", "Cervezas");
        Long activeId = repository.save(active);
        insertMovement(activeId, "ENTRY", 1);

        Product inactive = createProduct("Gaseosa B", "Gaseosas");
        Long inactiveId = repository.save(inactive);
        insertMovement(inactiveId, "ENTRY", 1);
        repository.deactivate(inactiveId);

        int result = repository.countLowStock();

        assertThat(result).isEqualTo(1);
    }

    @Test
    void countLowStockReturnsZeroWhenNoneBelow() throws SQLException {
        Product product = createProduct("Gaseosa B", "Gaseosas");
        Long productId = repository.save(product);
        insertMovement(productId, "ENTRY", 20);

        int result = repository.countLowStock();

        assertThat(result).isZero();
    }

    @Test
    void countOutOfStockCountsProductsWithZeroStock() throws SQLException {
        Product zero = createProduct("Cerveza A", "Cervezas");
        Long zeroId = repository.save(zero);
        insertMovement(zeroId, "ENTRY", 5);
        insertMovement(zeroId, "EXIT", 5);

        Product stocked = createProduct("Gaseosa B", "Gaseosas");
        Long stockedId = repository.save(stocked);
        insertMovement(stockedId, "ENTRY", 10);

        // untouched has no movements → computed stock = 0 → out of stock
        Product untouched = createProduct("Agua C", "Aguas");
        repository.save(untouched);

        int result = repository.countOutOfStock();

        assertThat(result).isEqualTo(2);
    }

    @Test
    void countOutOfStockIgnoresInactiveProducts() throws SQLException {
        Product active = createProduct("Cerveza A", "Cervezas");
        repository.save(active);

        Product inactive = createProduct("Gaseosa B", "Gaseosas");
        Long inactiveId = repository.save(inactive);
        insertMovement(inactiveId, "ENTRY", 3);
        insertMovement(inactiveId, "EXIT", 3);
        repository.deactivate(inactiveId);

        int result = repository.countOutOfStock();

        assertThat(result).isEqualTo(1);
    }

    @Test
    void countActiveProductsCountsOnlyActive() throws SQLException {
        Product active = createProduct("Cerveza A", "Cervezas");
        repository.save(active);
        Product second = createProduct("Gaseosa B", "Gaseosas");
        repository.save(second);
        Product inactive = createProduct("Agua C", "Aguas");
        Long inactiveId = repository.save(inactive);
        repository.deactivate(inactiveId);

        int result = repository.countActiveProducts();

        assertThat(result).isEqualTo(2);
    }

    @Test
    void countLowOrOutOfStockCountsBelowMinStock() throws SQLException {
        Product below = createProduct("Cerveza A", "Cervezas");
        below.setMinStock(10);
        Long belowId = repository.save(below);
        insertMovement(belowId, "ENTRY", 5);

        Product ok = createProduct("Gaseosa B", "Gaseosas");
        ok.setMinStock(5);
        Long okId = repository.save(ok);
        insertMovement(okId, "ENTRY", 20);

        int result = repository.countLowOrOutOfStock();

        assertThat(result).isEqualTo(1);
    }

    private void insertMovement(Long productId, String type, int quantity) throws SQLException {
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(
                "INSERT INTO stock_movements (product_id, movement_type, quantity) VALUES (?, ?, ?)")) {
            ps.setLong(1, productId);
            ps.setString(2, type);
            ps.setInt(3, quantity);
            ps.executeUpdate();
        }
    }

    @Test
    void findByCategoryAndFindDistinctCategoriesRemainDeprecated() throws Exception {
        assertThat(ProductRepository.class.getMethod("findByCategory", String.class)
                .isAnnotationPresent(Deprecated.class)).isTrue();
        assertThat(ProductRepository.class.getMethod("findDistinctCategories")
                .isAnnotationPresent(Deprecated.class)).isTrue();
    }
}
