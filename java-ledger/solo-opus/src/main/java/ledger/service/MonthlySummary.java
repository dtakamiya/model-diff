package ledger.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * 月次集計の結果。
 *
 * @param month              対象月
 * @param income             収入合計
 * @param expense            支出合計
 * @param expenseByCategory  カテゴリ別支出（金額降順、同額はカテゴリ昇順）
 */
public record MonthlySummary(YearMonth month, BigDecimal income, BigDecimal expense,
                             List<CategoryTotal> expenseByCategory) {

    public MonthlySummary {
        expenseByCategory = List.copyOf(expenseByCategory);
    }

    /** 残高（収入合計 − 支出合計）。 */
    public BigDecimal balance() {
        return income.subtract(expense);
    }

    /** カテゴリ 1 つ分の支出合計。 */
    public record CategoryTotal(String category, BigDecimal amount) {
    }
}
