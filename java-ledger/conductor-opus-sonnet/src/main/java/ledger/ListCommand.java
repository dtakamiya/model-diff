package ledger;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/** list: 日付昇順・ID 昇順で一覧をタブ区切り出力する。month が null なら全件。 */
public final class ListCommand implements Command {
    private final YearMonth month;

    public ListCommand(YearMonth month) {
        this.month = month;
    }

    @Override
    public String execute(LedgerRepository repo) {
        List<Entry> sorted = repo.load().entries().stream()
                .filter(e -> month == null || YearMonth.from(e.date()).equals(month))
                .sorted(Comparator.comparing(Entry::date).thenComparingLong(Entry::id))
                .toList();
        StringBuilder sb = new StringBuilder("id\tdate\ttype\tcategory\tamount\tmemo\n");
        for (Entry e : sorted) {
            // memo が空でも末尾のタブは省略しない
            sb.append(e.id()).append('\t').append(e.date()).append('\t')
              .append(e.type().label()).append('\t').append(e.category()).append('\t')
              .append(Money.format(e.amount())).append('\t').append(e.memo()).append('\n');
        }
        return sb.toString();
    }
}
