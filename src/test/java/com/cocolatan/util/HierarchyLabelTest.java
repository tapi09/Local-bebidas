package com.cocolatan.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HierarchyLabelTest {

    @Test
    void bothNullReturnsEmptyString() {
        assertThat(HierarchyLabel.resolve(null, null)).isEmpty();
    }

    @Test
    void bothBlankReturnsEmptyString() {
        assertThat(HierarchyLabel.resolve("", "")).isEmpty();
        assertThat(HierarchyLabel.resolve("   ", "  ")).isEmpty();
    }

    @Test
    void categoryOnlyReturnsCategory() {
        assertThat(HierarchyLabel.resolve("Cervezas", null)).isEqualTo("Cervezas");
        assertThat(HierarchyLabel.resolve("Cervezas", "")).isEqualTo("Cervezas");
    }

    @Test
    void categoryAndSubcategoryReturnsEmDashJoinedLabel() {
        assertThat(HierarchyLabel.resolve("Cervezas", "Latas")).isEqualTo("Cervezas — Latas");
    }

    @Test
    void subcategoryOnlyReturnsSubcategory() {
        assertThat(HierarchyLabel.resolve("", "Latas")).isEqualTo("Latas");
    }

    @Test
    void whitespaceAroundNamesIsTrimmed() {
        assertThat(HierarchyLabel.resolve(" Cervezas ", " Latas ")).isEqualTo("Cervezas — Latas");
    }
}
