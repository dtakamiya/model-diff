package ledger.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldParserTest {

    @Test
    void parsesValidDatesIncludingLeapDay() {
        assertEquals(LocalDate.of(2024, 2, 29), FieldParser.parseDate("2024-02-29"));
        assertEquals(LocalDate.of(2026, 1, 5), FieldParser.parseDate("2026-01-05"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-13-01", "2026/01/05", "2026-1-5", "2025-02-29", "", "２０２６-01-01"})
    void rejectsInvalidDates(String text) {
        assertThrows(IllegalArgumentException.class, () -> FieldParser.parseDate(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1200", "1200.5", "1200.50", "0.01", "999999999999.99"})
    void acceptsValidAmounts(String text) {
        assertEquals(2, FieldParser.parseAmount(text).scale());
    }

    @Test
    void normalizesAmountToTwoDecimals() {
        assertEquals(new BigDecimal("1200.50"), FieldParser.parseAmount("1200.5"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5", "12.345", "abc", "1e3", "1,200", "1200.", ".5", "+5", "1234567890123"})
    void rejectsInvalidAmounts(String text) {
        assertThrows(IllegalArgumentException.class, () -> FieldParser.parseAmount(text));
    }

    @Test
    void typeIsCaseSensitive() {
        assertEquals(EntryType.INCOME, FieldParser.parseType("income"));
        assertEquals(EntryType.EXPENSE, FieldParser.parseType("expense"));
        assertThrows(IllegalArgumentException.class, () -> FieldParser.parseType("Income"));
    }

    @Test
    void categoryKeepsSurroundingSpacesAndRejectsControls() {
        assertEquals(" 食費, \"x\" ", FieldParser.parseCategory(" 食費, \"x\" "));
        assertThrows(IllegalArgumentException.class, () -> FieldParser.parseCategory(""));
        assertThrows(IllegalArgumentException.class, () -> FieldParser.parseCategory("a\tb"));
        assertThrows(IllegalArgumentException.class, () -> FieldParser.parseMemo("a\nb"));
        assertEquals("", FieldParser.parseMemo(""));
    }

    @Test
    void parsesIdWithinLongRange() {
        assertEquals(Long.MAX_VALUE, FieldParser.parseId("9223372036854775807"));
        for (String bad : new String[] {"0", "01", "-1", "abc", "9223372036854775808"}) {
            assertThrows(IllegalArgumentException.class, () -> FieldParser.parseId(bad), bad);
        }
    }

    @Test
    void parsesMonth() {
        assertEquals(YearMonth.of(2026, 1), FieldParser.parseMonth("2026-01"));
        assertThrows(IllegalArgumentException.class, () -> FieldParser.parseMonth("2026-13"));
        assertThrows(IllegalArgumentException.class, () -> FieldParser.parseMonth("2026-1"));
    }
}
