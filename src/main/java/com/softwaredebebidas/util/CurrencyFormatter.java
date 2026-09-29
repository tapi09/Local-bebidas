package com.softwaredebebidas.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats monetary amounts in Argentine Peso (ARS) format: $XX.XXX,XX
 * Uses dot as thousands separator and comma as decimal separator.
 */
public final class CurrencyFormatter {

    private static final DecimalFormat FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "AR"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        FORMAT = new DecimalFormat("$#,##0.00", symbols);
    }

    private CurrencyFormatter() {
        // Utility class — no instantiation
    }

    /**
     * Formats a double value as ARS currency string.
     *
     * @param amount the amount to format
     * @return formatted string like "$12.500,75"
     */
    public static String format(double amount) {
        BigDecimal bd = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        return FORMAT.format(bd.doubleValue());
    }
}
