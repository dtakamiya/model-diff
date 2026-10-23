package ledger;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * 月次集計の結果。
 *
 * @param expenseByCategory 支出のみのカテゴリ別合計（金額降順、同額はカテゴリ名昇順）
 */
record Summary(
        YearMonth month,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance,
        List<CategoryTotal> expenseByCategory) {

    /** カテゴリ別の支出合計。 */
    record CategoryTotal(String category, BigDecimal amount) {
    }
}
