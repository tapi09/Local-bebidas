package com.cocolatan.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the pure logic extracted from CategoriesController
 * (delete-confirm warnings and sort_order parsing).
 */
class CategoriesControllerTest {

    @Test
    void buildCategoryDeleteWarningNamesProductCountWhenCategoryHasProducts() {
        String warning = CategoriesController.buildCategoryDeleteWarning("Cervezas", 4);

        assertThat(warning).contains("Cervezas");
        assertThat(warning).contains("4");
        assertThat(warning).contains("quedarán sin categoría");
    }

    @Test
    void buildCategoryDeleteWarningOmitsCountForEmptyCategory() {
        String warning = CategoriesController.buildCategoryDeleteWarning("Sidras", 0);

        assertThat(warning).contains("Sidras");
        assertThat(warning).doesNotContain("quedarán sin categoría");
    }

    @Test
    void buildSubcategoryDeleteWarningMentionsCategoryOnlyResult() {
        String warning = CategoriesController.buildSubcategoryDeleteWarning("Latas");

        assertThat(warning).contains("Latas");
        assertThat(warning).contains("categoría");
    }

    @Test
    void parseSortOrderParsesPositiveInteger() {
        assertThat(CategoriesController.parseSortOrder("  3 ", 0)).isEqualTo(3);
    }

    @Test
    void parseSortOrderFallsBackOnBlank() {
        assertThat(CategoriesController.parseSortOrder("   ", 5)).isEqualTo(5);
    }

    @Test
    void parseSortOrderFallsBackOnNonNumeric() {
        assertThat(CategoriesController.parseSortOrder("abc", 5)).isEqualTo(5);
    }

    @Test
    void moveTargetUpSwapsWithPreviousIndex() {
        assertThat(CategoriesController.moveTarget(2, true, 4)).isEqualTo(1);
    }

    @Test
    void moveTargetDownSwapsWithNextIndex() {
        assertThat(CategoriesController.moveTarget(0, false, 4)).isEqualTo(1);
    }

    @Test
    void moveTargetUpAtFirstRowStaysPut() {
        assertThat(CategoriesController.moveTarget(0, true, 4)).isEqualTo(0);
    }

    @Test
    void moveTargetDownAtLastRowStaysPut() {
        assertThat(CategoriesController.moveTarget(3, false, 4)).isEqualTo(3);
    }

    @Test
    void moveTargetWithSingleRowStaysPut() {
        assertThat(CategoriesController.moveTarget(0, true, 1)).isEqualTo(0);
        assertThat(CategoriesController.moveTarget(0, false, 1)).isEqualTo(0);
    }
}
