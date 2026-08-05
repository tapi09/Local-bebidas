package com.cocolatan.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubcategoryTest {

    @Test
    void createSubcategoryWithAllFields() {
        Subcategory subcategory = new Subcategory();
        subcategory.setId(1L);
        subcategory.setCategoryId(2L);
        subcategory.setName("Latas");
        subcategory.setSortOrder(1);
        subcategory.setActive(false);
        subcategory.setCreatedAt("2026-08-02 10:00:00");

        assertThat(subcategory.getId()).isEqualTo(1L);
        assertThat(subcategory.getCategoryId()).isEqualTo(2L);
        assertThat(subcategory.getName()).isEqualTo("Latas");
        assertThat(subcategory.getSortOrder()).isEqualTo(1);
        assertThat(subcategory.isActive()).isFalse();
        assertThat(subcategory.getCreatedAt()).isEqualTo("2026-08-02 10:00:00");
    }

    @Test
    void defaultSubcategoryIsActiveWithZeroSortOrder() {
        Subcategory subcategory = new Subcategory();
        assertThat(subcategory.isActive()).isTrue();
        assertThat(subcategory.getSortOrder()).isEqualTo(0);
    }
}
