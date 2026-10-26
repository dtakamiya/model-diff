package ledger;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** summary: 指定月の収入・支出・残高とカテゴリ別支出を出力する。 */
public final class SummaryCommand implements Command {
    private final YearMonth month;

    public SummaryCommand(YearMonth month) {
        this.month = month;
    }

    @Override
    public String execute(LedgerRepository repo) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new HashMap<>();
        for (Entry e : repo.load().entries()) {
            if (!YearMonth.from(e.date()).equals(month)) {
                continue;
            }
            if (e.type() == EntryType.INCOME) {
                income = income.add(e.amount());
            } else {
                expense = expense.add(e.amount());
                byCategory.merge(e.category(), e.amount(), BigDecimal::add);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("month\t").append(month).append('\n')
          .append("income\t").append(Money.format(income)).append('\n')
          .append("expense\t").append(Money.format(expense)).append('\n')
          .append("balance\t").append(Money.format(income.subtract(expense))).append('\n')
          .append("expense_by_category\n");
        // 金額降順、同額は category の String.compareTo 昇順
        List<Map.Entry<String, BigDecimal>> rows = new ArrayList<>(byCategory.entrySet());
        rows.sort((a, b) -> {
            int c = b.getValue().compareTo(a.getValue());
            return c != 0 ? c : a.getKey().compareTo(b.getKey());
        });
        for (Map.Entry<String, BigDecimal> r : rows) {
            sb.append(r.getKey()).append('\t').append(Money.format(r.getValue())).append('\n');
        }
        return sb.toString();
    }
}
