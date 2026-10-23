package ledger;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 月ごとの集計結果。金額はすべて {@link BigDecimal}（浮動小数は使わない）。
 *
 * @param month              集計対象の月
 * @param income             収入合計
 * @param expense            支出合計
 * @param balance            収入合計 − 支出合計（マイナスもありうる）
 * @param expenseByCategory  カテゴリ別の支出合計（金額降順、同額は category の昇順）
 */
public record Summary(
        YearMonth month,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance,
        List<CategoryTotal> expenseByCategory) {

    /** カテゴリ別の支出合計 1 件 */
    public record CategoryTotal(String category, BigDecimal amount) {
    }

    /** カテゴリ別支出の並び：金額の降順、同額は category を String.compareTo（UTF-16 順）で昇順 */
    private static final Comparator<CategoryTotal> CATEGORY_ORDER =
            Comparator.comparing(CategoryTotal::amount, Comparator.reverseOrder())
                    .thenComparing(CategoryTotal::category);

    /**
     * 指定月のレコードを集計する。該当データが無い月は 0.00 になる。
     * 収入のカテゴリは expenseByCategory に含めない。
     */
    public static Summary of(YearMonth month, List<Entry> entries) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        // TreeMap にしておくと、同じカテゴリの金額を一箇所に集められる
        Map<String, BigDecimal> expenseMap = new TreeMap<>();

        for (Entry entry : entries) {
            if (!YearMonth.from(entry.date()).equals(month)) {
                continue;
            }
            if (entry.type() == EntryType.INCOME) {
                income = income.add(entry.amount());
            } else {
                expense = expense.add(entry.amount());
                expenseMap.merge(entry.category(), entry.amount(), BigDecimal::add);
            }
        }

        List<CategoryTotal> byCategory = expenseMap.entrySet().stream()
                .map(e -> new CategoryTotal(e.getKey(), e.getValue().setScale(2)))
                .sorted(CATEGORY_ORDER)
                .collect(Collectors.toList());

        return new Summary(
                month,
                income.setScale(2),
                expense.setScale(2),
                income.subtract(expense).setScale(2),
                byCategory);
    }
}
