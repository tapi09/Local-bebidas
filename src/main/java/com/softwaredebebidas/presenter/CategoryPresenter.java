package com.softwaredebebidas.presenter;

import com.softwaredebebidas.model.Category;
import com.softwaredebebidas.model.Subcategory;
import com.softwaredebebidas.repository.CategoryRepository;
import com.softwaredebebidas.repository.SubcategoryRepository;

import java.sql.SQLException;
import java.util.List;

/**
 * Presenter for the category/subcategory management module (admin screen).
 * Orchestrates CRUD on both tables and translates SQL errors into Spanish
 * {@link RuntimeException}s for the view layer.
 */
public class CategoryPresenter {

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;

    public CategoryPresenter(CategoryRepository categoryRepository, SubcategoryRepository subcategoryRepository) {
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
    }

    /**
     * Returns all categories (including inactive) ordered by sort_order.
     */
    public List<Category> getCategories() {
        try {
            return categoryRepository.findAll();
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar categorías", e);
        }
    }

    /**
     * Returns only active categories, ordered by sort_order (cascade pickers).
     */
    public List<Category> getActiveCategories() {
        try {
            return categoryRepository.findAllActive();
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar categorías", e);
        }
    }

    /**
     * Saves a new category and returns it with the generated ID.
     */
    public Category saveCategory(Category category) {
        try {
            Long savedId = categoryRepository.save(category);
            category.setId(savedId);
            return category;
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar categoría", e);
        }
    }

    /**
     * Updates an existing category.
     */
    public void updateCategory(Category category) {
        try {
            categoryRepository.update(category);
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar categoría", e);
        }
    }

    /**
     * Flips the active flag of a category.
     */
    public void toggleActive(Category category) {
        try {
            categoryRepository.setActive(category.getId(), !category.isActive());
        } catch (SQLException e) {
            throw new RuntimeException("Error al cambiar estado de la categoría", e);
        }
    }

    /**
     * Deletes a category and orphans its products in a single transaction.
     */
    public void deleteCategory(long categoryId) {
        try {
            categoryRepository.deleteCategoryAndOrphans(categoryId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar categoría", e);
        }
    }

    /**
     * Counts products referencing a category (used for the delete confirm warning).
     */
    public int countProductsByCategory(long categoryId) {
        try {
            return categoryRepository.countProductsByCategory(categoryId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al contar productos de la categoría", e);
        }
    }

    /**
     * Returns all subcategories of a category, ordered by sort_order.
     */
    public List<Subcategory> getSubcategories(long categoryId) {
        try {
            return subcategoryRepository.findAllByCategoryId(categoryId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar subcategorías", e);
        }
    }

    /**
     * Returns only active subcategories of a category, ordered by sort_order.
     */
    public List<Subcategory> getActiveSubcategories(long categoryId) {
        try {
            return subcategoryRepository.findAllActiveByCategoryId(categoryId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar subcategorías", e);
        }
    }

    /**
     * Saves a new subcategory and returns it with the generated ID.
     */
    public Subcategory saveSubcategory(Subcategory subcategory) {
        try {
            Long savedId = subcategoryRepository.save(subcategory);
            subcategory.setId(savedId);
            return subcategory;
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar subcategoría", e);
        }
    }

    /**
     * Updates an existing subcategory.
     */
    public void updateSubcategory(Subcategory subcategory) {
        try {
            subcategoryRepository.update(subcategory);
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar subcategoría", e);
        }
    }

    /**
     * Flips the active flag of a subcategory.
     */
    public void toggleSubcategoryActive(Subcategory subcategory) {
        try {
            subcategoryRepository.setActive(subcategory.getId(), !subcategory.isActive());
        } catch (SQLException e) {
            throw new RuntimeException("Error al cambiar estado de la subcategoría", e);
        }
    }

    /**
     * Deletes a subcategory and orphans its products in a single transaction.
     */
    public void deleteSubcategory(long subcategoryId) {
        try {
            subcategoryRepository.deleteSubcategoryAndOrphans(subcategoryId);
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar subcategoría", e);
        }
    }

    /**
     * Returns whether a subcategory name already exists within a category.
     */
    public boolean existsSubcategoryName(long categoryId, String name) {
        try {
            return subcategoryRepository.existsByNameInCategory(categoryId, name);
        } catch (SQLException e) {
            throw new RuntimeException("Error al verificar subcategoría", e);
        }
    }
}
