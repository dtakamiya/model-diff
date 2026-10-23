package ledger.cli;

import ledger.domain.Entry;
import ledger.domain.Money;
import ledger.domain.Summary;

import java.util.List;

/**
 * 標準出力に出す表の書式。区切りはタブで、行末は LF。
 */
final class Report {

    private static final String TAB = "\t";
    private static final String NEWLINE = "\n";

    private Report() {
    }

    /** {@code list} の出力。0 件でもヘッダー行は出す。 */
    static String list(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(TAB, "id", "date", "type", "category", "amount", "memo")).append(NEWLINE);
        for (Entry entry : entries) {
            sb.append(String.join(TAB,
                    Long.toString(entry.id()),
                    entry.date().toString(),
                    entry.type().word(),
                    entry.category(),
                    Money.format(entry.amountCents()),
                    entry.memo())).append(NEWLINE);
        }
        return sb.toString();
    }

    /** {@code summary} の出力 */
    static String summary(Summary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("month", summary.month().toString()));
        sb.append(line("income", Money.format(summary.income())));
        sb.append(line("expense", Money.format(summary.expense())));
        sb.append(line("balance", Money.format(summary.balance())));
        sb.append("expense_by_category").append(NEWLINE);
        for (Summary.CategoryTotal total : summary.expenseByCategory()) {
            sb.append(line(total.category(), Money.format(total.amountCents())));
        }
        return sb.toString();
    }

    private static String line(String... columns) {
        return String.join(TAB, columns) + NEWLINE;
    }
}
