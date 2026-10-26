package ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SummaryTest {

    private static final YearMonth JAN = YearMonth.of(2026, 1);

    private static Entry entry(long id, LocalDate date, EntryType type, String category, String amount) {
        return new Entry(id, date, type, category, new BigDecimal(amount), "");
    }

    @Test
    @DisplayName("仕様の例どおりに集計・出力される")
    void matchesSpecExample() {
        List<Entry> entries = List.of(
                entry(1, LocalDate.of(2026, 1, 10), EntryType.EXPENSE, "food", "1200.50"),
                entry(2, LocalDate.of(2026, 1, 5), EntryType.INCOME, "salary", "300000.00"),
                entry(3, LocalDate.of(2026, 1, 10), EntryType.EXPENSE, "transport", "300.00"));

        assertEquals(List.of(
                "month\t2026-01",
                "income\t300000.00",
                "expense\t1500.50",
                "balance\t298499.50",
                "expense_by_category",
                "food\t1200.50",
                "transport\t300.00"),
                ReportFormatter.summary(Summary.of(JAN, entries)));
    }

    @Test
    @DisplayName("カテゴリ別支出は金額降順、同額はカテゴリ名の昇順")
    void categoriesSortedByAmountThenName() {
        List<Entry> entries = List.of(
                entry(1, LocalDate.of(2026, 1, 1), EntryType.EXPENSE, "b", "100"),
                entry(2, LocalDate.of(2026, 1, 2), EntryType.EXPENSE, "a", "100"),
                entry(3, LocalDate.of(2026, 1, 3), EntryType.EXPENSE, "c", "500"));

        Summary summary = Summary.of(JAN, entries);
        assertEquals(
                List.of("c", "a", "b"),
                summary.expenseByCategory().stream().map(Summary.CategoryTotal::category).toList());
    }

    @Test
    @DisplayName("収入のカテゴリは expense_by_category に含めない")
    void incomeCategoriesExcluded() {
        List<Entry> entries = List.of(
                entry(1, LocalDate.of(2026, 1, 1), EntryType.INCOME, "salary", "1000"));
        Summary summary = Summary.of(JAN, entries);
        assertTrue(summary.expenseByCategory().isEmpty());
        assertEquals("1000.00", ReportFormatter.amount(summary.income()));
    }

    @Test
    @DisplayName("残高がマイナスなら -500.00 の形式、該当データが無い月はすべて 0.00")
    void negativeBalanceAndEmptyMonth() {
        Summary negative = Summary.of(JAN, List.of(
                entry(1, LocalDate.of(2026, 1, 1), EntryType.EXPENSE, "food", "500")));
        assertEquals("-500.00", ReportFormatter.amount(negative.balance()));

        Summary empty = Summary.of(YearMonth.of(2025, 12), List.of(
                entry(1, LocalDate.of(2026, 1, 1), EntryType.EXPENSE, "food", "500")));
        assertEquals(List.of(
                "month\t2025-12",
                "income\t0.00",
                "expense\t0.00",
                "balance\t0.00",
                "expense_by_category"),
                ReportFormatter.summary(empty));
    }

    @Test
    @DisplayName("0.10 を 3 回の収入は 0.30（浮動小数の誤差が出ない）")
    void decimalSumIsExact() {
        List<Entry> entries = List.of(
                entry(1, LocalDate.of(2026, 1, 1), EntryType.INCOME, "x", "0.10"),
                entry(2, LocalDate.of(2026, 1, 2), EntryType.INCOME, "x", "0.10"),
                entry(3, LocalDate.of(2026, 1, 3), EntryType.INCOME, "x", "0.10"));
        assertEquals("income\t0.30", ReportFormatter.summary(Summary.of(JAN, entries)).get(1));
    }
}
