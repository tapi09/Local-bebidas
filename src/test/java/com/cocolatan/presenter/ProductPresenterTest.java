package com.cocolatan.presenter;

import com.cocolatan.model.Category;
import com.cocolatan.model.Product;
import com.cocolatan.model.Subcategory;
import com.cocolatan.model.Supplier;
import com.cocolatan.repository.CategoryRepository;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.SubcategoryRepository;
import com.cocolatan.repository.SupplierRepository;
import com.cocolatan.service.InventoryService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductPresenterTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SubcategoryRepository subcategoryRepository;

    private ProductPresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new ProductPresenter(productRepository, supplierRepository, inventoryService,
                categoryRepository, subcategoryRepository);
    }

    @Test
    void loadProductsCallsRepository() throws SQLException {
        when(productRepository.findAllActive()).thenReturn(Collections.emptyList());

        presenter.loadProducts();

        verify(productRepository).findAllActive();
    }

    @Test
    void loadProductsReturnsList() throws SQLException {
        List<Product> products = Arrays.asList(createProduct("Coca-Cola"), createProduct("Pepsi"));
        when(productRepository.findAllActive()).thenReturn(products);

        List<Product> result = presenter.loadProducts();

        assertThat(result).hasSize(2);
    }

    @Test
    void searchByNameDelegatesToRepository() throws SQLException {
        List<Product> results = Arrays.asList(createProduct("Coca-Cola"));
        when(productRepository.searchByName("coca")).thenReturn(results);

        List<Product> result = presenter.searchByName("coca");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Coca-Cola");
    }

    @Test
    void searchByNameReturnsEmptyWhenNoMatch() throws SQLException {
        when(productRepository.searchByName("xyz")).thenReturn(Collections.emptyList());

        List<Product> result = presenter.searchByName("xyz");

        assertThat(result).isEmpty();
    }

    @Test
    void saveProductValidatesNameRequired() throws SQLException {
        Product product = new Product();
        product.setName("");
        product.setCategory("Gaseosa");
        product.setPresentation("Botella");
        product.setCostPrice(350.0);
        product.setSalePrice(600.0);

        boolean result = presenter.saveProduct(product);

        assertThat(result).isFalse();
        verify(productRepository, never()).save(any());
    }

    @Test
    void saveProductAllowsBlankCategory() throws SQLException {
        Product product = new Product();
        product.setName("Coca-Cola");
        product.setCategory("");
        product.setPresentation("Botella");
        product.setCostPrice(350.0);
        product.setSalePrice(600.0);
        when(productRepository.save(any(Product.class))).thenReturn(1L);

        boolean result = presenter.saveProduct(product);

        assertThat(result).isTrue();
        verify(productRepository).save(product);
    }

    @Test
    void saveProductAllowsNullCategory() throws SQLException {
        Product product = new Product();
        product.setName("Coca-Cola");
        product.setCategory(null);
        product.setPresentation("Botella");
        product.setCostPrice(350.0);
        product.setSalePrice(600.0);
        when(productRepository.save(any(Product.class))).thenReturn(1L);

        boolean result = presenter.saveProduct(product);

        assertThat(result).isTrue();
        verify(productRepository).save(product);
        assertThat(product.getCategory()).isEqualTo("");
    }

    @Test
    void saveProductValidatesSalePriceAboveCost() throws SQLException {
        Product product = new Product();
        product.setName("Coca-Cola");
        product.setCategory("Gaseosa");
        product.setPresentation("Botella");
        product.setCostPrice(500.0);
        product.setSalePrice(400.0);

        boolean result = presenter.saveProduct(product);

        assertThat(result).isFalse();
        verify(productRepository, never()).save(any());
    }

    // --- validateProduct edge cases ---

    @Test
    void validateProductAcceptsValidProduct() {
        Product product = new Product();
        product.setName("Coca-Cola");
        product.setPresentation("Botella");
        product.setCostPrice(350.0);
        product.setSalePrice(600.0);
        product.setPedidosyaPrice(620.0);

        assertThat(presenter.validateProduct(product)).isTrue();
    }

    @Test
    void validateProductRejectsBlankPresentation() {
        Product product = new Product();
        product.setName("Coca-Cola");
        product.setPresentation("   ");

        assertThat(presenter.validateProduct(product)).isFalse();
    }

    @Test
    void validateProductRejectsNullPresentation() {
        Product product = new Product();
        product.setName("Coca-Cola");
        product.setPresentation(null);

        assertThat(presenter.validateProduct(product)).isFalse();
    }

    @Test
    void validateProductRejectsNegativeCostPrice() {
        Product product = new Product();
        product.setName("Coca-Cola");
        product.setPresentation("Botella");
        product.setCostPrice(-1.0);

        assertThat(presenter.validateProduct(product)).isFalse();
    }

    @Test
    void validateProductRejectsNegativePedidosyaPrice() {
        Product product = new Product();
        product.setName("Coca-Cola");
        product.setPresentation("Botella");
        product.setCostPrice(350.0);
        product.setSalePrice(600.0);
        product.setPedidosyaPrice(-5.0);

        assertThat(presenter.validateProduct(product)).isFalse();
    }

    @Test
    void validateProductAllowsZeroCostPrice() {
        Product product = new Product();
        product.setName("Coca-Cola");
        product.setPresentation("Botella");
        product.setCostPrice(0.0);
        product.setSalePrice(100.0);
        product.setPedidosyaPrice(100.0);

        assertThat(presenter.validateProduct(product)).isTrue();
    }

    @Test
    void saveProductSuccess() throws SQLException {
        Product product = createProduct("Coca-Cola");
        when(productRepository.save(any(Product.class))).thenReturn(1L);

        boolean result = presenter.saveProduct(product);

        assertThat(result).isTrue();
        verify(productRepository).save(product);
    }

    @Test
    void updateProductCallsRepository() throws SQLException {
        Product product = createProduct("Coca-Cola");
        product.setId(1L);

        presenter.updateProduct(product);

        verify(productRepository).update(product);
    }

    @Test
    void deactivateProductCallsRepository() throws SQLException {
        presenter.deactivateProduct(1L);

        verify(productRepository).deactivate(1L);
    }

    @Test
    void deactivateProductWithHistoryReturnsFalse() throws SQLException {
        when(productRepository.hasHistory(1L)).thenReturn(true);

        boolean result = presenter.safeDeactivateProduct(1L);

        assertThat(result).isFalse();
        verify(productRepository, never()).deactivate(1L);
    }

    @Test
    void deactivateProductWithoutHistorySucceeds() throws SQLException {
        when(productRepository.hasHistory(1L)).thenReturn(false);

        boolean result = presenter.safeDeactivateProduct(1L);

        assertThat(result).isTrue();
        verify(productRepository).deactivate(1L);
    }

    @Test
    void getDistinctCategoriesDelegates() throws SQLException {
        List<String> categories = Arrays.asList("Agua", "Gaseosa");
        when(productRepository.findDistinctCategories()).thenReturn(categories);

        List<String> result = presenter.getDistinctCategories();

        assertThat(result).containsExactly("Agua", "Gaseosa");
        verify(productRepository).findDistinctCategories();
    }

    @Test
    void loadSuppliersForDropdown() throws SQLException {
        List<Supplier> suppliers = Arrays.asList(createSupplier("Distribuidora Norte"));
        when(supplierRepository.findForDropdown()).thenReturn(suppliers);

        List<Supplier> result = presenter.loadSuppliers();

        assertThat(result).hasSize(1);
    }

    // --- hierarchy delegation (cascade pickers) ---

    @Test
    void getCategoriesDelegatesToCategoryRepository() throws SQLException {
        List<Category> categories = Arrays.asList(createCategory(1L, "Cervezas"), createCategory(2L, "Gaseosas"));
        when(categoryRepository.findAllActive()).thenReturn(categories);

        List<Category> result = presenter.getCategories();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Cervezas");
        verify(categoryRepository).findAllActive();
    }

    @Test
    void getCategoriesReturnsEmptyWhenNoneActive() throws SQLException {
        when(categoryRepository.findAllActive()).thenReturn(Collections.emptyList());

        assertThat(presenter.getCategories()).isEmpty();
    }

    @Test
    void getCategoriesThrowsRuntimeExceptionOnSqlException() throws SQLException {
        when(categoryRepository.findAllActive()).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.getCategories())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al cargar categorías");
    }

    @Test
    void getSubcategoriesForCategoryDelegatesToSubcategoryRepository() throws SQLException {
        List<Subcategory> subcategories = Arrays.asList(
                createSubcategory(1L, "Latas"), createSubcategory(2L, "Botella"));
        when(subcategoryRepository.findAllActiveByCategoryId(1L)).thenReturn(subcategories);

        List<Subcategory> result = presenter.getSubcategoriesForCategory(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Latas");
        verify(subcategoryRepository).findAllActiveByCategoryId(1L);
    }

    @Test
    void getSubcategoriesForCategoryReturnsEmptyWhenCategoryHasNone() throws SQLException {
        when(subcategoryRepository.findAllActiveByCategoryId(2L)).thenReturn(Collections.emptyList());

        assertThat(presenter.getSubcategoriesForCategory(2L)).isEmpty();
    }

    @Test
    void getSubcategoriesForCategoryThrowsRuntimeExceptionOnSqlException() throws SQLException {
        when(subcategoryRepository.findAllActiveByCategoryId(1L)).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.getSubcategoriesForCategory(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al cargar subcategorías");
    }

    private Category createCategory(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setActive(true);
        return category;
    }

    private Subcategory createSubcategory(Long id, String name) {
        Subcategory subcategory = new Subcategory();
        subcategory.setId(id);
        subcategory.setName(name);
        subcategory.setActive(true);
        return subcategory;
    }

    private Product createProduct(String name) {
        Product product = new Product();
        product.setName(name);
        product.setCategory("Gaseosa");
        product.setPresentation("Botella");
        product.setCostPrice(350.0);
        product.setSalePrice(600.0);
        product.setActive(true);
        return product;
    }

    private Supplier createSupplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName(name);
        return supplier;
    }
}
