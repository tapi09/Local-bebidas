package com.softwaredebebidas.util;

import java.time.LocalDate;

/**
 * Central stock-risk rules shared by the inventory and alert services:
 * the low-stock threshold comparisons and the expiry warning window.
 * <p>
 * Note that the codebase has TWO deliberate low-stock rules:
 * <ul>
 *   <li>{@code at-or-below minimum} ({@code stock <= min}) used by
 *       {@code InventoryService} and {@code StockPresenter} — a product
 *       sitting exactly at its minimum is flagged LOW.</li>
 *   <li>{@code strictly-below minimum} ({@code stock < min}) used by
 *       {@code AlertService} — a product exactly at its minimum is NOT low.</li>
 * </ul>
 * Keep these distinct; unifying them would change user-visible alert behavior.
 */
public final class StockRisk {

    /**
     * Number of days ahead that an expiring lot is flagged as "expiring soon".
     */
    public static final int EXPIRY_WARNING_DAYS = 7;

    private StockRisk() {
        // Utility class — no instantiation
    }

    /**
     * True when {@code currentStock} is at or below the minimum threshold.
     */
    public static boolean isAtOrBelowMinimum(int currentStock, int minStock) {
        return currentStock <= minStock;
    }

    /**
     * True when {@code currentStock} is strictly below the minimum threshold.
     */
    public static boolean isBelowMinimum(int currentStock, int minStock) {
        return currentStock < minStock;
    }

    /**
     * True when the expiry date falls within the warning window (inclusive of
     * today and of {@code now + EXPIRY_WARNING_DAYS}) but is not yet past.
     */
    public static boolean isExpiringSoon(LocalDate expiryDate, LocalDate now) {
        return !expiryDate.isBefore(now) && !expiryDate.isAfter(now.plusDays(EXPIRY_WARNING_DAYS));
    }

    /**
     * True when the expiry date is not after the end of the warning window.
     * Includes already-expired lots; used for alert counting.
     */
    public static boolean isExpiryAlert(LocalDate expiryDate, LocalDate now) {
        return !expiryDate.isAfter(now.plusDays(EXPIRY_WARNING_DAYS));
    }
}
