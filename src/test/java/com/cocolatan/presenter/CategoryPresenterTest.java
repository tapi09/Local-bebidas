package com.cocolatan.presenter;

import com.cocolatan.model.Category;
import com.cocolatan.model.Subcategory;
import com.cocolatan.repository.CategoryRepository;
import com.cocolatan.repository.SubcategoryRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryPresenterTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SubcategoryRepository subcategoryRepository;

    private CategoryPresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new CategoryPresenter(categoryRepository, subcategoryRepository);
    }

    // --- getCategories (admin screen: all, ordered by sort_order) ---

    @Test
    void getCategoriesDelegatesToRepository() throws SQLException {
        List<Category> categories = Arrays.asList(createCategory(1L, "Cervezas", 1), createCategory(2L, "Gaseosas", 2));
        when(categoryRepository.findAll()).thenReturn(categories);

        List<Category> result = presenter.getCategories();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Cervezas");
        verify(categoryRepository).findAll();
    }

    @Test
    void getCategoriesReturnsEmptyList() throws SQLException {
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());

        assertThat(presenter.getCategories()).isEmpty();
    }

    @Test
    void getCategoriesThrowsRuntimeExceptionOnSqlException() throws SQLException {
        when(categoryRepository.findAll()).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.getCategories())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al cargar categorías");
    }

    // --- getActiveCategories (cascade pickers) ---

    @Test
    void getActiveCategoriesDelegatesToRepository() throws SQLException {
        List<Category> categories = Collections.singletonList(createCategory(1L, "Cervezas", 1));
        when(categoryRepository.findAllActive()).thenReturn(categories);

        List<Category> result = presenter.getActiveCategories();

        assertThat(result).hasSize(1);
        verify(categoryRepository).findAllActive();
    }

    @Test
    void getActiveCategoriesThrowsRuntimeExceptionOnSqlException() throws SQLException {
        when(categoryRepository.findAllActive()).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.getActiveCategories())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al cargar categorías");
    }

    // --- saveCategory ---

    @Test
    void saveCategoryWritesBackGeneratedId() throws SQLException {
        Category category = createCategory(null, "Cervezas", 1);
        when(categoryRepository.save(any(Category.class))).thenReturn(42L);

        Category result = presenter.saveCategory(category);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(category.getId()).isEqualTo(42L);
        verify(categoryRepository).save(category);
    }

    @Test
    void saveCategoryThrowsRuntimeExceptionOnSqlException() throws SQLException {
        when(categoryRepository.save(any(Category.class))).thenThrow(new SQLException("UNIQUE constraint failed"));

        assertThatThrownBy(() -> presenter.saveCategory(createCategory(null, "Cervezas", 1)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al guardar categoría");
    }

    // --- updateCategory ---

    @Test
    void updateCategoryDelegatesToRepository() throws SQLException {
        Category category = createCategory(1L, "Cervezas", 1);

        presenter.updateCategory(category);

        verify(categoryRepository).update(category);
    }

    @Test
    void updateCategoryThrowsRuntimeExceptionOnSqlException() throws SQLException {
        doThrow(new SQLException("DB error"))
                .when(categoryRepository).update(any(Category.class));

        assertThatThrownBy(() -> presenter.updateCategory(createCategory(1L, "Cervezas", 1)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al actualizar categoría");
    }

    // --- toggleActive ---

    @Test
    void toggleActiveFlipsActiveState() throws SQLException {
        Category category = createCategory(1L, "Cervezas", 1);
        category.setActive(false);

        presenter.toggleActive(category);

        verify(categoryRepository).setActive(1L, true);
    }

    @Test
    void toggleActiveDeactivatesWhenActive() throws SQLException {
        Category category = createCategory(1L, "Cervezas", 1);

        presenter.toggleActive(category);

        verify(categoryRepository).setActive(1L, false);
    }

    @Test
    void toggleActiveThrowsRuntimeExceptionOnSqlException() throws SQLException {
        doThrowSqlExceptionOnSetActive();

        assertThatThrownBy(() -> presenter.toggleActive(createCategory(1L, "Cervezas", 1)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al cambiar estado de la categoría");
    }

    // --- deleteCategory (orphan-safe) ---

    @Test
    void deleteCategoryDelegatesToDeleteOrphans() throws SQLException {
        presenter.deleteCategory(9L);

        verify(categoryRepository).deleteCategoryAndOrphans(9L);
    }

    @Test
    void deleteCategoryThrowsRuntimeExceptionOnSqlException() throws SQLException {
        doThrow(new SQLException("DB error"))
                .when(categoryRepository).deleteCategoryAndOrphans(9L);

        assertThatThrownBy(() -> presenter.deleteCategory(9L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al eliminar categoría");
    }

    // --- countProductsByCategory (delete-with-confirm warning path) ---

    @Test
    void countProductsByCategoryDelegatesForDeleteConfirm() throws SQLException {
        when(categoryRepository.countProductsByCategory(7L)).thenReturn(3);

        int count = presenter.countProductsByCategory(7L);

        assertThat(count).isEqualTo(3);
        verify(categoryRepository).countProductsByCategory(7L);
    }

    @Test
    void countProductsByCategoryReturnsZeroWhenNoProducts() throws SQLException {
        when(categoryRepository.countProductsByCategory(7L)).thenReturn(0);

        assertThat(presenter.countProductsByCategory(7L)).isZero();
    }

    @Test
    void countProductsByCategoryThrowsRuntimeExceptionOnSqlException() throws SQLException {
        when(categoryRepository.countProductsByCategory(7L)).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.countProductsByCategory(7L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al contar productos de la categoría");
    }

    // --- subcategory delegation (admin subcategories table) ---

    @Test
    void getSubcategoriesDelegatesToRepository() throws SQLException {
        List<Subcategory> subcategories = Arrays.asList(createSubcategory(1L, 1L, "Latas"), createSubcategory(2L, 1L, "Botella"));
        when(subcategoryRepository.findAllByCategoryId(1L)).thenReturn(subcategories);

        List<Subcategory> result = presenter.getSubcategories(1L);

        assertThat(result).hasSize(2);
        verify(subcategoryRepository).findAllByCategoryId(1L);
    }

    @Test
    void getActiveSubcategoriesDelegatesToRepository() throws SQLException {
        List<Subcategory> subcategories = Collections.singletonList(createSubcategory(1L, 1L, "Latas"));
        when(subcategoryRepository.findAllActiveByCategoryId(1L)).thenReturn(subcategories);

        List<Subcategory> result = presenter.getActiveSubcategories(1L);

        assertThat(result).hasSize(1);
        verify(subcategoryRepository).findAllActiveByCategoryId(1L);
    }

    @Test
    void getSubcategoriesThrowsRuntimeExceptionOnSqlException() throws SQLException {
        when(subcategoryRepository.findAllByCategoryId(1L)).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.getSubcategories(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al cargar subcategorías");
    }

    @Test
    void saveSubcategoryWritesBackGeneratedId() throws SQLException {
        Subcategory subcategory = createSubcategory(null, 1L, "Latas");
        when(subcategoryRepository.save(any(Subcategory.class))).thenReturn(15L);

        Subcategory result = presenter.saveSubcategory(subcategory);

        assertThat(result.getId()).isEqualTo(15L);
        assertThat(subcategory.getId()).isEqualTo(15L);
        verify(subcategoryRepository).save(subcategory);
    }

    @Test
    void saveSubcategoryThrowsRuntimeExceptionOnSqlException() throws SQLException {
        when(subcategoryRepository.save(any(Subcategory.class))).thenThrow(new SQLException("UNIQUE constraint failed"));

        assertThatThrownBy(() -> presenter.saveSubcategory(createSubcategory(null, 1L, "Latas")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al guardar subcategoría");
    }

    @Test
    void updateSubcategoryDelegatesToRepository() throws SQLException {
        Subcategory subcategory = createSubcategory(1L, 1L, "Latas");

        presenter.updateSubcategory(subcategory);

        verify(subcategoryRepository).update(subcategory);
    }

    @Test
    void toggleSubcategoryActiveFlipsActiveState() throws SQLException {
        Subcategory subcategory = createSubcategory(1L, 1L, "Latas");
        subcategory.setActive(false);

        presenter.toggleSubcategoryActive(subcategory);

        verify(subcategoryRepository).setActive(1L, true);
    }

    @Test
    void deleteSubcategoryDelegatesToDeleteOrphans() throws SQLException {
        presenter.deleteSubcategory(5L);

        verify(subcategoryRepository).deleteSubcategoryAndOrphans(5L);
    }

    @Test
    void deleteSubcategoryThrowsRuntimeExceptionOnSqlException() throws SQLException {
        doThrow(new SQLException("DB error"))
                .when(subcategoryRepository).deleteSubcategoryAndOrphans(5L);

        assertThatThrownBy(() -> presenter.deleteSubcategory(5L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al eliminar subcategoría");
    }

    @Test
    void existsSubcategoryNameDelegatesToRepository() throws SQLException {
        when(subcategoryRepository.existsByNameInCategory(1L, "Latas")).thenReturn(true);

        assertThat(presenter.existsSubcategoryName(1L, "Latas")).isTrue();
        verify(subcategoryRepository).existsByNameInCategory(1L, "Latas");
    }

    private void doThrowSqlExceptionOnSetActive() throws SQLException {
        doThrow(new SQLException("DB error"))
                .when(categoryRepository).setActive(any(Long.class), any(boolean.class));
    }

    private Category createCategory(Long id, String name, int sortOrder) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSortOrder(sortOrder);
        category.setActive(true);
        return category;
    }

    private Subcategory createSubcategory(Long id, Long categoryId, String name) {
        Subcategory subcategory = new Subcategory();
        subcategory.setId(id);
        subcategory.setCategoryId(categoryId);
        subcategory.setName(name);
        subcategory.setActive(true);
        return subcategory;
    }
}
