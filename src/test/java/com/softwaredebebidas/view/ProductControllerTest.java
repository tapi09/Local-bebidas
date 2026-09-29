package com.softwaredebebidas.view;

import com.softwaredebebidas.model.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the pure hierarchy filter logic extracted from ProductController.
 */
class ProductControllerTest {

    private Product cervezaLatas() {
        Product p = new Product();
        p.setName("Quilmes 1L");
        p.setSku("SKU-00001");
        p.setCategoryId(1L);
        p.setSubcategoryId(10L);
        p.setCategoryName("Cervezas");
        p.setSubcategoryName("Latas");
        p.setBarcode("7790000000001");
        return p;
    }

    private Product cervezaBotella() {
        Product p = new Product();
        p.setName("Quilmes Botella");
        p.setSku("SKU-00002");
        p.setCategoryId(1L);
        p.setSubcategoryId(11L);
        p.setCategoryName("Cervezas");
        p.setSubcategoryName("Botella");
        p.setBarcode("7790000000002");
        return p;
    }

    private Product gaseosa() {
        Product p = new Product();
        p.setName("Coca-Cola 500ml");
        p.setSku("SKU-00003");
        p.setCategoryId(2L);
        p.setCategoryName("Gaseosas");
        p.setBarcode("7790000000003");
        return p;
    }

    private Product uncategorized() {
        Product p = new Product();
        p.setName("Sifón");
        p.setSku("SKU-00004");
        p.setCategoryId(null);
        p.setSubcategoryId(null);
        p.setCategoryName(null);
        p.setSubcategoryName(null);
        p.setBarcode("7790000000009");
        return p;
    }

    @Test
    void nullCategoryFilterMatchesEveryProduct() {
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), null, null, null)).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(uncategorized(), null, null, null)).isTrue();
    }

    @Test
    void uncategorizedFilterMatchesOnlyProductsWithoutCategory() {
        assertThat(ProductController.matchesHierarchyFilter(uncategorized(),
                ProductController.UNCATEGORIZED_ID, null, null)).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(),
                ProductController.UNCATEGORIZED_ID, null, null)).isFalse();
        assertThat(ProductController.matchesHierarchyFilter(gaseosa(),
                ProductController.UNCATEGORIZED_ID, null, null)).isFalse();
    }

    @Test
    void categoryFilterMatchesOnlyProductsOfThatCategory() {
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), 1L, null, null)).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(cervezaBotella(), 1L, null, null)).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(gaseosa(), 1L, null, null)).isFalse();
    }

    @Test
    void subcategoryFilterNarrowsWithinCategory() {
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), 1L, 10L, null)).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(cervezaBotella(), 1L, 10L, null)).isFalse();
    }

    @Test
    void nullSubcategoryFilterMatchesAnySubcategory() {
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), 1L, null, null)).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(cervezaBotella(), 1L, null, null)).isTrue();
    }

    @Test
    void subcategoryFilterIsIgnoredWhenCategoryNotFiltered() {
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), null, 10L, null)).isTrue();
    }

    @Test
    void filterAndSearchUseAndSemantics() {
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), 1L, 10L, "quilmes")).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), 1L, 10L, "coca")).isFalse();
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), 1L, null, "coca")).isFalse();
    }

    @Test
    void searchMatchesSubcategoryAndCategoryNames() {
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), null, null, "latas")).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), null, null, "cervezas")).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(gaseosa(), null, null, "latas")).isFalse();
    }

    @Test
    void searchMatchesByBarcodeAndSku() {
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), null, null, "7790000000001")).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), null, null, "sku-00001")).isTrue();
    }

    @Test
    void uncategorizedFilterAndSearchAreAnd() {
        assertThat(ProductController.matchesHierarchyFilter(uncategorized(),
                ProductController.UNCATEGORIZED_ID, null, "sif")).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(uncategorized(),
                ProductController.UNCATEGORIZED_ID, null, "quilmes")).isFalse();
    }

    @Test
    void blankQueryMatchesEverything() {
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), 1L, 10L, "")).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(cervezaLatas(), 1L, 10L, "   ")).isTrue();
    }

    @Test
    void nullCategoryNameDoesNotThrowOnSearch() {
        Product p = new Product();
        p.setName("Generico");
        p.setCategoryId(1L);
        p.setCategoryName(null);
        p.setSubcategoryName(null);

        assertThat(ProductController.matchesHierarchyFilter(p, 1L, null, "generico")).isTrue();
        assertThat(ProductController.matchesHierarchyFilter(p, 1L, null, "latas")).isFalse();
    }
}
