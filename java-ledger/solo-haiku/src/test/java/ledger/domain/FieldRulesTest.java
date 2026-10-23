package ledger.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldRulesTest {

    @Test
    void 実在する日付だけを受け付ける() {
        assertEquals(LocalDate.of(2024, 2, 29), FieldRules.date("2024-02-29").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-13-01", "2026/01/05", "2026-1-5", "26-01-05", "2026-01-05 "})
    void 不正な日付は空になる(String text) {
        assertFalse(FieldRules.date(text).isPresent());
    }

    @Test
    void 月は01から12のみ受け付ける() {
        assertEquals(YearMonth.of(2026, 1), FieldRules.month("2026-01").orElseThrow());
        assertFalse(FieldRules.month("2026-00").isPresent());
        assertFalse(FieldRules.month("2026-13").isPresent());
        assertFalse(FieldRules.month("2026-1").isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "10", "9223372036854775807"})
    void 先頭0なしの正の整数はID(String text) {
        assertTrue(FieldRules.id(text).isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "01", "9223372036854775808", ""})
    void 不正なIDは空になる(String text) {
        assertFalse(FieldRules.id(text).isPresent());
    }

    @Test
    void 種別は小文字のincomeとexpenseのみ() {
        assertEquals(EntryType.INCOME, FieldRules.type("income").orElseThrow());
        assertEquals(EntryType.EXPENSE, FieldRules.type("expense").orElseThrow());
        assertFalse(FieldRules.type("Income").isPresent());
        assertFalse(FieldRules.type("transfer").isPresent());
    }

    @Test
    void カテゴリは空とタブや改行を拒否し空白と日本語は許可する() {
        assertTrue(FieldRules.isValidCategory("食費 ケーキ"));
        assertTrue(FieldRules.isValidCategory(" 前後の空白 "));
        assertFalse(FieldRules.isValidCategory(""));
        assertFalse(FieldRules.isValidCategory("a\tb"));
        assertFalse(FieldRules.isValidCategory("a\nb"));
        assertFalse(FieldRules.isValidCategory("a\rb"));
    }

    @Test
    void メモは空でもよいがタブや改行は拒否する() {
        assertTrue(FieldRules.isPlainText(""));
        assertTrue(FieldRules.isPlainText("lunch, with \"Taro\""));
        assertFalse(FieldRules.isPlainText("a\tb"));
    }
}
