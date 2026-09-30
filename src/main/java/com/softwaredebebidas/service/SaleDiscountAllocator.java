package com.softwaredebebidas.service;

import com.softwaredebebidas.model.SaleItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Allocates the NET amount of a sale to each of its lines.
 *
 * <p>Business rule: the only real discount is sale-level (sales.discount), so
 * reports that break a sale down by product must spread it proportionally to
 * each line's gross amount ({@code quantity * unitPrice}). This keeps per-product
 * figures consistent with {@code sales.total_amount}, which is already net.
 *
 * <p>Works on historic sales too because it only needs the items and the
 * persisted net total; no schema information about the discount is required.
 *
 * <p>All rounding is done in whole cents (half-up, like SaleRepository.round2)
 * so the allocated lines add up to the net total exactly.
 */
public final class SaleDiscountAllocator {

    private SaleDiscountAllocator() {
    }

    /**
     * Returns the net amount of each item, in the same order as {@code items}
     * (result.get(i) belongs to items.get(i)).
     *
     * <ul>
     *   <li>net_i = round2(gross_i * netTotal / grossSum)</li>
     *   <li>The rounding remainder (netTotal - sum of net_i) is added to the line
     *       with the largest gross amount (the first one on ties), so the sum of
     *       the result equals {@code netTotal} exactly (to the cent).</li>
     *   <li>If grossSum is not positive every line is 0 (avoids dividing by zero).</li>
     * </ul>
     *
     * @param items    the sale's items
     * @param netTotal the sale's net total (sale.getTotalAmount())
     */
    public static List<Double> allocateNet(List<SaleItem> items, double netTotal) {
        int size = items.size();
        double grossSum = 0.0;
        for (SaleItem item : items) {
            grossSum += gross(item);
        }

        List<Double> result = new ArrayList<>(size);
        if (grossSum <= 0.0) {
            for (int i = 0; i < size; i++) {
                result.add(0.0);
            }
            return result;
        }

        long netTotalCents = Math.round(netTotal * 100.0);
        long[] cents = new long[size];
        long allocated = 0;
        int largest = 0;
        for (int i = 0; i < size; i++) {
            double gross = gross(items.get(i));
            cents[i] = Math.round(gross * netTotalCents / grossSum);
            allocated += cents[i];
            if (gross > gross(items.get(largest))) {
                largest = i;
            }
        }
        cents[largest] += netTotalCents - allocated;

        for (long c : cents) {
            result.add(c / 100.0);
        }
        return result;
    }

    private static double gross(SaleItem item) {
        return item.getQuantity() * item.getUnitPrice();
    }
}
