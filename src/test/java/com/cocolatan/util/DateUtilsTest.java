package com.cocolatan.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateUtilsTest {

    @Test
    void formatLocalDateToDDMMYYYY() {
        LocalDate date = LocalDate.of(2026, 7, 22);
        String result = DateUtils.format(date);
        assertThat(result).isEqualTo("22/07/2026");
    }

    @Test
    void formatFirstDayOfMonth() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        String result = DateUtils.format(date);
        assertThat(result).isEqualTo("01/01/2026");
    }

    @Test
    void parseDDMMYYYYToLocalDate() {
        LocalDate result = DateUtils.parse("22/07/2026");
        assertThat(result).isEqualTo(LocalDate.of(2026, 7, 22));
    }

    @Test
    void parseFirstDayOfMonth() {
        LocalDate result = DateUtils.parse("01/01/2026");
        assertThat(result).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void roundTripPreservesDate() {
        LocalDate original = LocalDate.of(2026, 12, 31);
        String formatted = DateUtils.format(original);
        LocalDate parsed = DateUtils.parse(formatted);
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void parseInvalidFormatThrowsException() {
        assertThatThrownBy(() -> DateUtils.parse("2026-07-22"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseEmptyStringThrowsException() {
        assertThatThrownBy(() -> DateUtils.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatNullThrowsException() {
        assertThatThrownBy(() -> DateUtils.format(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("parse lanza IllegalArgumentException para fecha con día inválido")
    void parseInvalidDay_throwsException() {
        assertThatThrownBy(() -> DateUtils.parse("32/01/2026"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parse lanza IllegalArgumentException para fecha con mes inválido")
    void parseInvalidMonth_throwsException() {
        assertThatThrownBy(() -> DateUtils.parse("01/13/2026"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parse lanza IllegalArgumentException para null")
    void parseNull_throwsException() {
        assertThatThrownBy(() -> DateUtils.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
