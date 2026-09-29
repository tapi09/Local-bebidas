package com.softwaredebebidas.util;

/**
 * Central helper for the display label "Category — Subcategory".
 * Em-dash separator (" — ") matches the legacy category format.
 */
public final class HierarchyLabel {

    private static final String SEPARATOR = " — ";

    private HierarchyLabel() {
        // Utility class — no instantiation
    }

    /**
     * Resolves the display label for a category/subcategory pair.
     *
     * @param categoryName    resolved category name (may be null or blank)
     * @param subcategoryName resolved subcategory name (may be null or blank)
     * @return "" when both are blank, the non-blank part(s) joined by " — " otherwise
     */
    public static String resolve(String categoryName, String subcategoryName) {
        String category = categoryName == null ? "" : categoryName.trim();
        String subcategory = subcategoryName == null ? "" : subcategoryName.trim();
        if (category.isEmpty() && subcategory.isEmpty()) {
            return "";
        }
        if (category.isEmpty()) {
            return subcategory;
        }
        return subcategory.isEmpty() ? category : category + SEPARATOR + subcategory;
    }
}
