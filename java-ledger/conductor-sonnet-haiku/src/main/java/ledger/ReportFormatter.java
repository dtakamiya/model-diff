package ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 標準出力の書式を組み立てるクラス。仕様どおりの 1 行 1 要素の文字列を返す（改行は含まない）。
 * 金額は常に小数 2 桁・3 桁区切りなし。
 */
public final class ReportFormatter {

    /** list の見出し行（列はタブ区切り） */
    private static final String LIST_HEADER = "id\tdate\ttype\tcategory\tamount\tmemo";

    private ReportFormatter() {
    }

    /** 金額を小数 2 桁の文字列にする（例 1200 → 1200.00、-500 → -500.00） */
    public static String amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** list の出力行。見出し行の後に 1 件 1 行。memo が空でも末尾のタブは省略しない */
    public static List<String> entryList(List<Entry> entries) {
        List<String> lines = new ArrayList<>();
        lines.add(LIST_HEADER);
        for (Entry entry : entries) {
            lines.add(String.join("\t",
                    String.valueOf(entry.id()),
                    entry.date().toString(),
                    entry.type().label(),
                    entry.category(),
                    amount(entry.amount()),
                    entry.memo()));
        }
        return lines;
    }

    /** summary の出力行（仕様の書式どおり） */
    public static List<String> summary(Summary summary) {
        List<String> lines = new ArrayList<>();
        lines.add("month\t" + summary.month());
        lines.add("income\t" + amount(summary.income()));
        lines.add("expense\t" + amount(summary.expense()));
        lines.add("balance\t" + amount(summary.balance()));
        lines.add("expense_by_category");
        for (Summary.CategoryTotal total : summary.expenseByCategory()) {
            lines.add(total.category() + "\t" + amount(total.amount()));
        }
        return lines;
    }
}
