package com.cocolatan.repository;

import com.cocolatan.model.Category;
import com.cocolatan.model.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryRepositoryTest {

    private DatabaseManager dbManager;
    private CategoryRepository repository;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
        repository = new CategoryRepository(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    private void clearHierarchy() throws SQLException {
        try (Statement stmt = dbManager.getConnection().createStatement()) {
            stmt.execute("DELETE FROM subcategories");
            stmt.execute("DELETE FROM categories");
        }
    }

    private Long insertSubcategory(long categoryId, String name) throws SQLException {
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

    private Long insertProduct(String legacy, Long categoryId, Long subcategoryId) throws SQLException {
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(
                "INSERT INTO products (name, category, presentation, cost_price, sale_price, category_id, subcategory_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Producto de prueba");
            ps.setString(2, legacy == null ? "" : legacy);
            ps.setString(3, "Botella");
            ps.setDouble(4, 100.0);
            ps.setDouble(5, 200.0);
            if (categoryId != null) {
                ps.setLong(6, categoryId);
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            if (subcategoryId != null) {
                ps.setLong(7, subcategoryId);
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new SQLException("Failed to retrieve generated product ID");
            }
        }
    }

    private int countSubcategories(long categoryId) throws SQLException {
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM subcategories WHERE category_id = ?")) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private Category createCategory(String name, int sortOrder) {
        Category category = new Category();
        category.setName(name);
        category.setSortOrder(sortOrder);
        category.setActive(true);
        return category;
    }

    @Test
    void saveReturnsIdAndWritesBackId() throws SQLException {
        Category category = createCategory("Categoría Nueva", 5);

        Long id = repository.save(category);

        assertThat(id).isNotNull();
        assertThat(id).isGreaterThan(0);
        assertThat(category.getId()).isEqualTo(id);
    }

    @Test
    void findByIdReturnsSavedCategory() throws SQLException {
        Long id = repository.save(createCategory("Vinos Tintos", 4));

        Optional<Category> found = repository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Vinos Tintos");
        assertThat(found.get().getSortOrder()).isEqualTo(4);
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    void findByIdReturnsEmptyForNonexistent() throws SQLException {
        Optional<Category> found = repository.findById(999L);
        assertThat(found).isEmpty();
    }

    @Test
    void findAllOrdersBySortOrderThenNameNocase() throws SQLException {
        clearHierarchy();
        repository.save(createCategory("Zebra", 2));
        repository.save(createCategory("bravo", 1));
        repository.save(createCategory("Alpha", 1));

        List<Category> all = repository.findAll();

        assertThat(all).extracting(Category::getName)
                .containsExactly("Alpha", "bravo", "Zebra");
    }

    @Test
    void findAllActiveExcludesInactive() throws SQLException {
        clearHierarchy();
        Long activeId = repository.save(createCategory("Activa", 1));
        Long inactiveId = repository.save(createCategory("Inactiva", 2));
        repository.setActive(inactiveId, false);

        List<Category> active = repository.findAllActive();

        assertThat(active).extracting(Category::getId).containsExactly(activeId);
    }

    @Test
    void updateModifiesCategory() throws SQLException {
        Long id = repository.save(createCategory("Original", 1));

        Category update = new Category();
        update.setId(id);
        update.setName("Renombrada");
        update.setSortOrder(7);
        update.setActive(false);
        repository.update(update);

        Optional<Category> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Renombrada");
        assertThat(found.get().getSortOrder()).isEqualTo(7);
        assertThat(found.get().isActive()).isFalse();
    }

    @Test
    void setActiveTogglesActiveFlag() throws SQLException {
        Long id = repository.save(createCategory("Toggle", 1));

        repository.setActive(id, false);
        assertThat(repository.findById(id).orElseThrow().isActive()).isFalse();

        repository.setActive(id, true);
        assertThat(repository.findById(id).orElseThrow().isActive()).isTrue();
    }

    @Test
    void saveRejectsDuplicateName() throws SQLException {
        repository.save(createCategory("Única", 1));

        assertThatThrownBy(() -> repository.save(createCategory("Única", 2)))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void existsByNameReturnsTrueForExistingName() throws SQLException {
        repository.save(createCategory("Aguas Saborizadas", 3));

        assertThat(repository.existsByName("Aguas Saborizadas")).isTrue();
    }

    @Test
    void existsByNameReturnsFalseForMissingName() throws SQLException {
        assertThat(repository.existsByName("No Existe")).isFalse();
    }

    @Test
    void countProductsByCategoryIncludesSubcategoryProducts() throws SQLException {
        clearHierarchy();
        Long catId = repository.save(createCategory("Cervezas", 1));
        Long subId = insertSubcategory(catId, "Latas");
        insertProduct(null, catId, null);
        insertProduct(null, catId, subId);
        Long other = repository.save(createCategory("Otras", 2));
        insertProduct(null, other, null);

        assertThat(repository.countProductsByCategory(catId)).isEqualTo(2);
    }

    @Test
    void countProductsByCategoryReturnsZeroWhenNoProducts() throws SQLException {
        clearHierarchy();
        Long catId = repository.save(createCategory("Vacía", 1));

        assertThat(repository.countProductsByCategory(catId)).isZero();
    }

    @Test
    void deleteCategoryAndOrphansOrphansProductsAndDeletesHierarchy() throws SQLException {
        clearHierarchy();
        Long catId = repository.save(createCategory("Cervezas", 1));
        Long subId = insertSubcategory(catId, "Latas");
        Long orphanWithSub = insertProduct("Cervezas — Latas", catId, subId);
        insertProduct(null, catId, null);
        Long other = repository.save(createCategory("Aguas", 2));
        Long survivor = insertProduct("Aguas", other, null);

        repository.deleteCategoryAndOrphans(catId);

        assertThat(repository.findById(catId)).isEmpty();
        assertThat(countSubcategories(catId)).isZero();

        ProductRepository productRepository = new ProductRepository(dbManager);
        Optional<Product> orphaned = productRepository.findById(orphanWithSub);
        assertThat(orphaned).isPresent();
        assertThat(orphaned.get().getCategoryId()).isNull();
        assertThat(orphaned.get().getSubcategoryId()).isNull();
        assertThat(orphaned.get().getCategory()).isEmpty();

        Optional<Product> surviving = productRepository.findById(survivor);
        assertThat(surviving).isPresent();
        assertThat(surviving.get().getCategoryId()).isEqualTo(other);
        assertThat(surviving.get().getCategory()).isEqualTo("Aguas");
    }
}
