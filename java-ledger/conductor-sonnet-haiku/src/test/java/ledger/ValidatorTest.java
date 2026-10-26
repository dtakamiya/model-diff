package ledger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-05", "2024-02-29"})
    @DisplayName("実在する日付は有効")
    void validDate(String value) {
        assertDoesNotThrow(() -> Validator.parseDate(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-13-01", "2025-02-29", "2026/01/05", "2026-1-5", "abc", ""})
    @DisplayName("不正な日付は入力エラー")
    void invalidDate(String value) {
        assertThrows(InputException.class, () -> Validator.parseDate(value));
    }

    @Test
    @DisplayName("日付は LocalDate になる")
    void dateIsLocalDate() {
        assertEquals(LocalDate.of(2024, 2, 29), Validator.parseDate("2024-02-29"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1200", "1200.5", "1200.50", "0.01", "999999999999"})
    @DisplayName("正しい金額は有効")
    void validAmount(String value) {
        assertDoesNotThrow(() -> Validator.parseAmount(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5", "12.345", "abc", "1e3", "1,200", "1200.", ".5", "+5", "1234567890123"})
    @DisplayName("不正な金額は入力エラー")
    void invalidAmount(String value) {
        assertThrows(InputException.class, () -> Validator.parseAmount(value));
    }

    @Test
    @DisplayName("金額は BigDecimal で精度を保つ")
    void amountKeepsPrecision() {
        assertEquals(0, new BigDecimal("0.10").compareTo(Validator.parseAmount("0.10")));
    }

    @Test
    @DisplayName("種別は小文字の income と expense のみ")
    void typeIsLowercaseOnly() {
        assertEquals(EntryType.INCOME, Validator.parseType("income"));
        assertEquals(EntryType.EXPENSE, Validator.parseType("expense"));
        assertThrows(InputException.class, () -> Validator.parseType("Income"));
        assertThrows(InputException.class, () -> Validator.parseType("EXPENSE"));
        assertThrows(InputException.class, () -> Validator.parseType(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", " 食費 ", "食,費", "say \"hi\""})
    @DisplayName("カテゴリは空白や記号を含んでも有効（trim しない）")
    void categoryAllowsSymbols(String value) {
        assertEquals(value, Validator.parseCategory(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a\tb", "a\rb", "a\nb"})
    @DisplayName("カテゴリが空やタブ改行を含むと入力エラー")
    void categoryRejectsEmptyOrControl(String value) {
        assertThrows(InputException.class, () -> Validator.parseCategory(value));
    }

    @Test
    @DisplayName("メモは空文字と記号を許し、タブ改行は拒否")
    void memoRules() {
        assertEquals("", Validator.parseMemo(""));
        assertEquals("lunch, \"x\"", Validator.parseMemo("lunch, \"x\""));
        assertThrows(InputException.class, () -> Validator.parseMemo("a\tb"));
        assertThrows(InputException.class, () -> Validator.parseMemo("a\nb"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "42", "9223372036854775807"})
    @DisplayName("ID は正の整数で Long の最大値以下なら有効")
    void validId(String value) {
        assertEquals(Long.parseLong(value), Validator.parseId(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "01", "-1", "abc", "9223372036854775808", ""})
    @DisplayName("不正な ID は入力エラー")
    void invalidId(String value) {
        assertThrows(InputException.class, () -> Validator.parseId(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01", "2026-12"})
    @DisplayName("月は yyyy-MM で 01 から 12 まで有効")
    void validMonth(String value) {
        assertDoesNotThrow(() -> Validator.parseMonth(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-00", "2026-13", "2026-1", "2026/01", ""})
    @DisplayName("不正な月は入力エラー")
    void invalidMonth(String value) {
        assertThrows(InputException.class, () -> Validator.parseMonth(value));
    }
}
