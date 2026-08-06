package com.cocolatan.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StockRiskTest {

    private static final LocalDate NOW = LocalDate.of(2026, 8, 5);

    // --- isAtOrBelowMinimum ---

    @Test
    void isAtOrBelowMinimumIsTrueWhenEqual() {
        assertThat(StockRisk.isAtOrBelowMinimum(10, 10)).isTrue();
    }

    @Test
    void isAtOrBelowMinimumIsTrueWhenBelow() {
        assertThat(StockRisk.isAtOrBelowMinimum(5, 10)).isTrue();
    }

    @Test
    void isAtOrBelowMinimumIsFalseWhenAbove() {
        assertThat(StockRisk.isAtOrBelowMinimum(11, 10)).isFalse();
    }

    // --- isBelowMinimum ---

    @Test
    void isBelowMinimumIsFalseWhenEqual() {
        assertThat(StockRisk.isBelowMinimum(10, 10)).isFalse();
    }

    @Test
    void isBelowMinimumIsTrueWhenBelow() {
        assertThat(StockRisk.isBelowMinimum(5, 10)).isTrue();
    }

    @Test
    void isBelowMinimumIsFalseWhenAbove() {
        assertThat(StockRisk.isBelowMinimum(11, 10)).isFalse();
    }

    // --- isExpiringSoon ---

    @Test
    void isExpiringSoonIsTrueWithinWindow() {
        assertThat(StockRisk.isExpiringSoon(NOW.plusDays(3), NOW)).isTrue();
    }

    @Test
    void isExpiringSoonIsTrueForToday() {
        assertThat(StockRisk.isExpiringSoon(NOW, NOW)).isTrue();
    }

    @Test
    void isExpiringSoonIsTrueForExactlySevenDays() {
        assertThat(StockRisk.isExpiringSoon(NOW.plusDays(7), NOW)).isTrue();
    }

    @Test
    void isExpiringSoonIsFalseBeyondWindow() {
        assertThat(StockRisk.isExpiringSoon(NOW.plusDays(8), NOW)).isFalse();
    }

    @Test
    void isExpiringSoonIsFalseForExpiredDate() {
        assertThat(StockRisk.isExpiringSoon(NOW.minusDays(1), NOW)).isFalse();
    }

    // --- isExpiryAlert ---

    @Test
    void isExpiryAlertIsTrueWithinWindow() {
        assertThat(StockRisk.isExpiryAlert(NOW.plusDays(5), NOW)).isTrue();
    }

    @Test
    void isExpiryAlertIsTrueForExpiredDate() {
        assertThat(StockRisk.isExpiryAlert(NOW.minusDays(5), NOW)).isTrue();
    }

    @Test
    void isExpiryAlertIsTrueForExactlySevenDays() {
        assertThat(StockRisk.isExpiryAlert(NOW.plusDays(7), NOW)).isTrue();
    }

    @Test
    void isExpiryAlertIsFalseBeyondWindow() {
        assertThat(StockRisk.isExpiryAlert(NOW.plusDays(8), NOW)).isFalse();
    }

    @Test
    void warningDaysConstantIsSeven() {
        assertThat(StockRisk.EXPIRY_WARNING_DAYS).isEqualTo(7);
    }
}
