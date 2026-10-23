package ledger.cli;

import ledger.model.Entry;
import ledger.service.MonthlySummary;

import java.math.BigDecimal;
import java.util.List;

/** コマンドの結果を標準出力用のタブ区切りテキストに整形する。 */
final class OutputFormatter {

    private static final String LIST_HEADER = "id\tdate\ttype\tcategory\tamount\tmemo";

    private OutputFormatter() {
    }

    /** 一覧：ヘッダー行 + 1 件 1 行（常に 6 列。memo が空でも末尾のタブを残す）。 */
    static String formatList(List<Entry> entries) {
        StringBuilder sb = new StringBuilder(LIST_HEADER).append('\n');
        for (Entry e : entries) {
            sb.append(e.id()).append('\t')
                    .append(e.date()).append('\t')
                    .append(e.type().label()).append('\t')
                    .append(e.category()).append('\t')
                    .append(money(e.amount())).append('\t')
                    .append(e.memo()).append('\n');
        }
        return sb.toString();
    }

    /** 月次集計：収支・残高のあと、カテゴリ別支出を並べる。 */
    static String formatSummary(MonthlySummary s) {
        StringBuilder sb = new StringBuilder();
        sb.append("month\t").append(s.month()).append('\n');
        sb.append("income\t").append(money(s.income())).append('\n');
        sb.append("expense\t").append(money(s.expense())).append('\n');
        sb.append("balance\t").append(money(s.balance())).append('\n');
        sb.append("expense_by_category\n");
        for (MonthlySummary.CategoryTotal t : s.expenseByCategory()) {
            sb.append(t.category()).append('\t').append(money(t.amount())).append('\n');
        }
        return sb.toString();
    }

    /** 金額を小数 2 桁・3 桁区切りなしで表す（負数は先頭に -）。 */
    static String money(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }
}
