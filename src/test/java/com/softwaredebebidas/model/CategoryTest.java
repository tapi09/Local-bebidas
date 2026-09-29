package com.softwaredebebidas.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTest {

    @Test
    void createCategoryWithAllFields() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Cervezas");
        category.setSortOrder(1);
        category.setActive(false);
        category.setCreatedAt("2026-08-02 10:00:00");

        assertThat(category.getId()).isEqualTo(1L);
        assertThat(category.getName()).isEqualTo("Cervezas");
        assertThat(category.getSortOrder()).isEqualTo(1);
        assertThat(category.isActive()).isFalse();
        assertThat(category.getCreatedAt()).isEqualTo("2026-08-02 10:00:00");
    }

    @Test
    void defaultCategoryIsActiveWithZeroSortOrder() {
        Category category = new Category();
        assertThat(category.isActive()).isTrue();
        assertThat(category.getSortOrder()).isEqualTo(0);
    }
}
