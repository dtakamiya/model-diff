package ledger;

import java.util.List;

/** コマンドの標準出力書式を組み立てる。行区切りは常に LF。 */
final class OutputFormatter {

    private OutputFormatter() {}

    /** ヘッダー行 + 1 件 1 行（常に 6 列。memo が空でも末尾のタブは省略しない）。 */
    static String list(List<Entry> entries) {
        StringBuilder sb = new StringBuilder("id\tdate\ttype\tcategory\tamount\tmemo\n");
        for (Entry e : entries) {
            sb.append(e.id()).append('\t')
              .append(e.date()).append('\t')
              .append(e.type()).append('\t')
              .append(e.category()).append('\t')
              .append(Money.format(e.amount())).append('\t')
              .append(e.memo()).append('\n');
        }
        return sb.toString();
    }

    /** 月次集計の出力書式。 */
    static String summary(Summary s) {
        StringBuilder sb = new StringBuilder();
        appendLine(sb, "month", s.month().toString());
        appendLine(sb, "income", Money.format(s.income()));
        appendLine(sb, "expense", Money.format(s.expense()));
        appendLine(sb, "balance", Money.format(s.balance()));
        sb.append("expense_by_category\n");
        for (Summary.CategoryTotal total : s.expenseByCategory()) {
            appendLine(sb, total.category(), Money.format(total.amount()));
        }
        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, String key, String value) {
        sb.append(key).append('\t').append(value).append('\n');
    }
}
