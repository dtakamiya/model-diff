package ledger.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerTest {

    private static final YearMonth JAN = YearMonth.of(2026, 1);

    @Test
    void 削除したIDは再利用しない() {
        Ledger ledger = Ledger.empty();
        ledger.add(LocalDate.of(2026, 1, 1), EntryType.EXPENSE, "food", 100, "");
        ledger.add(LocalDate.of(2026, 1, 2), EntryType.EXPENSE, "food", 200, "");
        assertTrue(ledger.delete(2));

        assertEquals(3, ledger.add(LocalDate.of(2026, 1, 3), EntryType.EXPENSE, "food", 300, ""));
        assertEquals(4, ledger.nextId());
    }

    @Test
    void 存在しないIDの削除はfalseでnextIdは変わらない() {
        Ledger ledger = Ledger.empty();
        assertFalse(ledger.delete(99));
        assertEquals(1, ledger.nextId());
    }

    @Test
    void 一覧は日付昇順で同日はID昇順() {
        Ledger ledger = Ledger.empty();
        ledger.add(LocalDate.of(2026, 1, 10), EntryType.EXPENSE, "food", 120050, "");
        ledger.add(LocalDate.of(2026, 1, 5), EntryType.INCOME, "salary", 30000000, "");
        ledger.add(LocalDate.of(2026, 1, 10), EntryType.EXPENSE, "transport", 30000, "train");

        List<Long> ids = ledger.list(Optional.empty()).stream().map(Entry::id).toList();
        assertEquals(List.of(2L, 1L, 3L), ids);
    }

    @Test
    void 月指定の一覧はその月だけ() {
        Ledger ledger = Ledger.empty();
        ledger.add(LocalDate.of(2026, 1, 10), EntryType.EXPENSE, "food", 100, "");
        ledger.add(LocalDate.of(2026, 2, 1), EntryType.EXPENSE, "food", 100, "");

        assertEquals(1, ledger.list(Optional.of(JAN)).size());
    }

    @Test
    void 集計は収入と支出を分けカテゴリ別は金額降順で同額は名前順() {
        Ledger ledger = Ledger.empty();
        ledger.add(LocalDate.of(2026, 1, 5), EntryType.INCOME, "salary", 30000000, "");
        ledger.add(LocalDate.of(2026, 1, 10), EntryType.EXPENSE, "transport", 30000, "");
        ledger.add(LocalDate.of(2026, 1, 10), EntryType.EXPENSE, "food", 120050, "");
        ledger.add(LocalDate.of(2026, 1, 11), EntryType.EXPENSE, "book", 30000, "");
        ledger.add(LocalDate.of(2026, 2, 1), EntryType.EXPENSE, "food", 999900, "");

        Summary summary = ledger.summary(JAN);

        assertEquals(30000000, summary.income());
        assertEquals(180050, summary.expense());
        assertEquals(29819950, summary.balance());
        assertEquals(List.of(
                new Summary.CategoryTotal("food", 120050),
                new Summary.CategoryTotal("book", 30000),
                new Summary.CategoryTotal("transport", 30000)),
                summary.expenseByCategory());
    }

    @Test
    void データの無い月の集計は全部0() {
        Summary summary = Ledger.empty().summary(JAN);

        assertEquals(0, summary.income());
        assertEquals(0, summary.expense());
        assertEquals(0, summary.balance());
        assertTrue(summary.expenseByCategory().isEmpty());
    }

    @Test
    void 復元時にIDが採番済み以上なら拒否する() {
        Entry entry = new Entry(5, LocalDate.of(2026, 1, 1), EntryType.EXPENSE, "food", 100, "");
        assertThrows(IllegalArgumentException.class, () -> new Ledger(5, List.of(entry)));
    }
}
