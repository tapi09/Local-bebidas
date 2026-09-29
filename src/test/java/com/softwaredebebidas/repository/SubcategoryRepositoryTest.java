package com.softwaredebebidas.repository;

import com.softwaredebebidas.model.Category;
import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Subcategory;
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

class SubcategoryRepositoryTest {

    private DatabaseManager dbManager;
    private SubcategoryRepository repository;
    private CategoryRepository categoryRepository;
    private Long categoryA;
    private Long categoryB;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = DatabaseManager.createInMemory();
        repository = new SubcategoryRepository(dbManager);
        categoryRepository = new CategoryRepository(dbManager);
        try (Statement stmt = dbManager.getConnection().createStatement()) {
            stmt.execute("DELETE FROM subcategories");
            stmt.execute("DELETE FROM categories");
        }
        categoryA = categoryRepository.save(createCategory("Cat A", 1));
        categoryB = categoryRepository.save(createCategory("Cat B", 2));
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    private Category createCategory(String name, int sortOrder) {
        Category category = new Category();
        category.setName(name);
        category.setSortOrder(sortOrder);
        category.setActive(true);
        return category;
    }

    private Subcategory createSubcategory(Long categoryId, String name, int sortOrder) {
        Subcategory subcategory = new Subcategory();
        subcategory.setCategoryId(categoryId);
        subcategory.setName(name);
        subcategory.setSortOrder(sortOrder);
        subcategory.setActive(true);
        return subcategory;
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

    @Test
    void saveReturnsIdAndWritesBackId() throws SQLException {
        Subcategory subcategory = createSubcategory(categoryA, "Latas", 1);

        Long id = repository.save(subcategory);

        assertThat(id).isNotNull();
        assertThat(id).isGreaterThan(0);
        assertThat(subcategory.getId()).isEqualTo(id);
    }

    @Test
    void findByIdReturnsSavedSubcategory() throws SQLException {
        Long id = repository.save(createSubcategory(categoryA, "Latas", 1));

        Optional<Subcategory> found = repository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Latas");
        assertThat(found.get().getCategoryId()).isEqualTo(categoryA);
        assertThat(found.get().getSortOrder()).isEqualTo(1);
    }

    @Test
    void findByIdReturnsEmptyForNonexistent() throws SQLException {
        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void findAllByCategoryIdReturnsOnlyThatCategoryOrdered() throws SQLException {
        repository.save(createSubcategory(categoryB, "Zulu", 1));
        repository.save(createSubcategory(categoryA, "Latas", 2));
        repository.save(createSubcategory(categoryA, "Alpha", 1));
        repository.save(createSubcategory(categoryA, "Botella", 2));

        List<Subcategory> subs = repository.findAllByCategoryId(categoryA);

        assertThat(subs).extracting(Subcategory::getName)
                .containsExactly("Alpha", "Botella", "Latas");
    }

    @Test
    void findAllActiveByCategoryIdFiltersInactive() throws SQLException {
        Long activeId = repository.save(createSubcategory(categoryA, "Activa", 1));
        Long inactiveId = repository.save(createSubcategory(categoryA, "Inactiva", 2));
        repository.setActive(inactiveId, false);

        List<Subcategory> subs = repository.findAllActiveByCategoryId(categoryA);

        assertThat(subs).extracting(Subcategory::getId).containsExactly(activeId);
    }

    @Test
    void updateModifiesSubcategory() throws SQLException {
        Long id = repository.save(createSubcategory(categoryA, "Original", 1));

        Subcategory update = new Subcategory();
        update.setId(id);
        update.setCategoryId(categoryA);
        update.setName("Renombrada");
        update.setSortOrder(9);
        update.setActive(false);
        repository.update(update);

        Optional<Subcategory> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Renombrada");
        assertThat(found.get().getSortOrder()).isEqualTo(9);
        assertThat(found.get().isActive()).isFalse();
    }

    @Test
    void setActiveTogglesActiveFlag() throws SQLException {
        Long id = repository.save(createSubcategory(categoryA, "Toggle", 1));

        repository.setActive(id, false);
        assertThat(repository.findById(id).orElseThrow().isActive()).isFalse();

        repository.setActive(id, true);
        assertThat(repository.findById(id).orElseThrow().isActive()).isTrue();
    }

    @Test
    void saveRejectsDuplicateNameInCategory() throws SQLException {
        repository.save(createSubcategory(categoryA, "Latas", 1));

        assertThatThrownBy(() -> repository.save(createSubcategory(categoryA, "Latas", 2)))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void saveAllowsSameNameInDifferentCategory() throws SQLException {
        Long inA = repository.save(createSubcategory(categoryA, "Latas", 1));
        Long inB = repository.save(createSubcategory(categoryB, "Latas", 1));

        assertThat(inA).isNotNull();
        assertThat(inB).isNotNull();
        assertThat(inA).isNotEqualTo(inB);
    }

    @Test
    void existsByNameInCategoryScopesByName() throws SQLException {
        repository.save(createSubcategory(categoryA, "Latas", 1));
        repository.save(createSubcategory(categoryB, "Latas", 1));

        assertThat(repository.existsByNameInCategory(categoryA, "Latas")).isTrue();
        assertThat(repository.existsByNameInCategory(categoryB, "Latas")).isTrue();
        assertThat(repository.existsByNameInCategory(categoryA, "No Existe")).isFalse();
        assertThat(repository.existsByNameInCategory(categoryB, "Botella")).isFalse();
    }

    @Test
    void deleteSubcategoryAndOrphansOrphansProductsAndRecomputesLegacy() throws SQLException {
        Long latasId = repository.save(createSubcategory(categoryA, "Latas", 1));
        Long botellaId = repository.save(createSubcategory(categoryA, "Botella", 2));
        Long orphan = insertProduct("Cat A — Latas", categoryA, latasId);
        Long safe = insertProduct("Cat A — Botella", categoryA, botellaId);

        repository.deleteSubcategoryAndOrphans(latasId);

        assertThat(repository.findById(latasId)).isEmpty();

        ProductRepository productRepository = new ProductRepository(dbManager);
        Optional<Product> orphaned = productRepository.findById(orphan);
        assertThat(orphaned).isPresent();
        assertThat(orphaned.get().getSubcategoryId()).isNull();
        assertThat(orphaned.get().getCategoryId()).isEqualTo(categoryA);
        assertThat(orphaned.get().getCategory()).isEqualTo("Cat A");

        Optional<Product> surviving = productRepository.findById(safe);
        assertThat(surviving).isPresent();
        assertThat(surviving.get().getSubcategoryId()).isEqualTo(botellaId);
        assertThat(surviving.get().getCategory()).isEqualTo("Cat A — Botella");
    }
}
