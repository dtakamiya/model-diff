package ledger.domain;

import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1 か月分の集計結果。金額はすべて銭単位。
 *
 * @param month            集計対象の月
 * @param income           収入合計
 * @param expense          支出合計
 * @param balance          収入合計 − 支出合計
 * @param expenseByCategory カテゴリ別の支出合計（金額降順、同額はカテゴリ名の昇順）
 */
public record Summary(
        YearMonth month,
        long income,
        long expense,
        long balance,
        List<CategoryTotal> expenseByCategory) {

    /** カテゴリ別の支出合計 */
    public record CategoryTotal(String category, long amountCents) {
    }

    private static final Comparator<CategoryTotal> BY_AMOUNT_DESC_THEN_CATEGORY =
            Comparator.comparingLong(CategoryTotal::amountCents)
                    .reversed()
                    .thenComparing(CategoryTotal::category);

    /**
     * 指定月の記録だけを集計する。該当が無い月は合計がすべて 0。
     */
    public static Summary of(YearMonth month, Collection<Entry> entries) {
        long income = 0;
        long expense = 0;
        Map<String, Long> expenseByCategory = new HashMap<>();
        for (Entry entry : entries) {
            if (!YearMonth.from(entry.date()).equals(month)) {
                continue;
            }
            if (entry.type() == EntryType.INCOME) {
                income = Math.addExact(income, entry.amountCents());
            } else {
                expense = Math.addExact(expense, entry.amountCents());
                expenseByCategory.merge(entry.category(), entry.amountCents(), (a, b) -> Math.addExact(a, b));
            }
        }
        List<CategoryTotal> categoryTotals = expenseByCategory.entrySet().stream()
                .map(e -> new CategoryTotal(e.getKey(), e.getValue()))
                .sorted(BY_AMOUNT_DESC_THEN_CATEGORY)
                .toList();
        return new Summary(month, income, expense, Math.subtractExact(income, expense), categoryTotals);
    }
}
