package ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Validator の境界値テスト（spec の有効例・不正例を網羅）。 */
class ValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-13-01", "2026/01/05", "2026-1-5", "2025-02-29", "2026-00-10", "20260105", ""})
    void rejectsInvalidDates(String s) {
        assertThrows(InputException.class, () -> Validator.date(s));
    }

    @Test
    void acceptsValidDates() {
        assertEquals(LocalDate.of(2024, 2, 29), Validator.date("2024-02-29"));
        assertEquals(LocalDate.of(2026, 1, 5), Validator.date("2026-01-05"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Income", "EXPENSE", "", " expense", "expense ", "transfer"})
    void rejectsInvalidTypes(String s) {
        assertThrows(InputException.class, () -> Validator.type(s));
    }

    @Test
    void acceptsValidTypes() {
        assertEquals("income", Validator.type("income"));
        assertEquals("expense", Validator.type("expense"));
    }

    @Test
    void categoryKeepsSpacesAndAcceptsCommaQuoteAndJapanese() {
        assertEquals(" 食費 ", Validator.category(" 食費 "));
        assertEquals("a,b", Validator.category("a,b"));
        assertEquals("a\"b", Validator.category("a\"b"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a\tb", "a\nb", "a\rb"})
    void rejectsInvalidCategories(String s) {
        assertThrows(InputException.class, () -> Validator.category(s));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5", "12.345", "abc", "1e3", "1,200", "1200.", ".5", "+5", "1234567890123", " 1"})
    void rejectsInvalidAmounts(String s) {
        assertThrows(InputException.class, () -> Validator.amount(s));
    }

    @Test
    void amountIsNormalizedToTwoDecimals() {
        assertEquals(new BigDecimal("1200.00"), Validator.amount("1200"));
        assertEquals(new BigDecimal("1200.50"), Validator.amount("1200.5"));
        assertEquals(new BigDecimal("1200.50"), Validator.amount("1200.50"));
        assertEquals(new BigDecimal("0.01"), Validator.amount("0.01"));
        assertEquals("999999999999.00", Validator.amount("999999999999").toPlainString());
    }

    @Test
    void memoAcceptsEmptyAndCommaQuoteAndJapanese() {
        assertEquals("", Validator.memo(""));
        assertEquals("lunch, \"x\"", Validator.memo("lunch, \"x\""));
        assertEquals("ランチ", Validator.memo("ランチ"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\tb", "a\nb", "a\rb"})
    void rejectsMemoWithTabOrLineBreak(String s) {
        assertThrows(InputException.class, () -> Validator.memo(s));
    }

    @Test
    void acceptsValidMonths() {
        assertEquals(YearMonth.of(2026, 1), Validator.month("2026-01"));
        assertEquals(YearMonth.of(2026, 12), Validator.month("2026-12"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-00", "2026-13", "2026-1", "2026/01", "202601", "2026-01-01", ""})
    void rejectsInvalidMonths(String s) {
        assertThrows(InputException.class, () -> Validator.month(s));
    }

    @Test
    void acceptsValidIds() {
        assertEquals(1L, Validator.id("1"));
        assertEquals(10L, Validator.id("10"));
        assertEquals(Long.MAX_VALUE, Validator.id("9223372036854775807"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "01", "-1", "abc", "9223372036854775808", "", "1 ", "+1"})
    void rejectsInvalidIds(String s) {
        assertThrows(InputException.class, () -> Validator.id(s));
    }
}
