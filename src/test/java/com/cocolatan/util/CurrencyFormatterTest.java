package com.cocolatan.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyFormatterTest {

    @Test
    void formatZeroReturnsDollarSign() {
        String result = CurrencyFormatter.format(0.0);
        assertThat(result).isEqualTo("$0,00");
    }

    @Test
    void formatSmallAmountWithDecimals() {
        String result = CurrencyFormatter.format(150.50);
        assertThat(result).isEqualTo("$150,50");
    }

    @Test
    void formatLargeAmountWithThousandsSeparator() {
        String result = CurrencyFormatter.format(12500.75);
        assertThat(result).isEqualTo("$12.500,75");
    }

    @Test
    void formatExactThousands() {
        String result = CurrencyFormatter.format(1000.00);
        assertThat(result).isEqualTo("$1.000,00");
    }

    @Test
    void formatMillionsAmount() {
        String result = CurrencyFormatter.format(1500000.00);
        assertThat(result).isEqualTo("$1.500.000,00");
    }

    @Test
    void formatRoundsToTwoDecimals() {
        String result = CurrencyFormatter.format(100.999);
        assertThat(result).isEqualTo("$101,00");
    }

    @Test
    void formatNegativeAmount() {
        String result = CurrencyFormatter.format(-500.00);
        assertThat(result).isEqualTo("-$500,00");
    }

    @Test
    @DisplayName("Formateo de valor muy grande con múltiples separadores de miles")
    void formatVeryLargeValue() {
        String result = CurrencyFormatter.format(9999999.99);
        assertThat(result).isEqualTo("$9.999.999,99");
    }
}
