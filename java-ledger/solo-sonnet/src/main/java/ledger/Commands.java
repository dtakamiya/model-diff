package ledger;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 各コマンドの実装。引数の検証 → データ読み込み → 処理の順に行い、
 * 標準出力へ出す文字列を返す（出力は呼び出し側で行う）。
 */
public final class Commands {
    private final LedgerRepository repository;

    public Commands(LedgerRepository repository) {
        this.repository = repository;
    }

    public String add(List<String> args) throws LedgerException {
        if (args.size() != 4 && args.size() != 5) {
            throw LedgerException.input("usage: add <yyyy-MM-dd> <income|expense> <category> <amount> [memo]");
        }
        LocalDate date = Validators.date(args.get(0));
        EntryType type = Validators.type(args.get(1));
        String category = Validators.category(args.get(2));
        long cents = Validators.amount(args.get(3));
        String memo = Validators.memo(args.size() == 5 ? args.get(4) : "");

        Ledger ledger = repository.load();
        long id = ledger.add(date, type, category, cents, memo);
        repository.save(ledger);
        return id + "\n";
    }

    public String list(List<String> args) throws LedgerException {
        String month = parseOptionalMonth(args);
        Ledger ledger = repository.load();

        StringBuilder sb = new StringBuilder("id\tdate\ttype\tcategory\tamount\tmemo\n");
        ledger.entries().stream()
                .filter(e -> month == null || inMonth(e, month))
                .sorted(Comparator.comparing(Entry::date).thenComparingLong(Entry::id))
                .forEach(e -> sb.append(e.id()).append('\t')
                        .append(e.date()).append('\t')
                        .append(e.type().label()).append('\t')
                        .append(e.category()).append('\t')
                        .append(Money.format(e.amountCents())).append('\t')
                        .append(e.memo()).append('\n'));
        return sb.toString();
    }

    public String delete(List<String> args) throws LedgerException {
        if (args.size() != 1) {
            throw LedgerException.input("usage: delete <id>");
        }
        long id = Validators.id(args.get(0));

        Ledger ledger = repository.load();
        if (!ledger.delete(id)) {
            throw LedgerException.notFound("id " + id + " は存在しません");
        }
        repository.save(ledger);
        return "deleted " + id + "\n";
    }

    public String summary(List<String> args) throws LedgerException {
        if (args.size() != 2 || !args.get(0).equals("--month")) {
            throw LedgerException.input("usage: summary --month yyyy-MM");
        }
        String month = Validators.month(args.get(1));
        Ledger ledger = repository.load();

        long income = 0;
        long expense = 0;
        // TreeMap は String.compareTo 順。後続の安定ソートにより同額は category 昇順のまま残る
        Map<String, Long> byCategory = new TreeMap<>();
        for (Entry e : ledger.entries()) {
            if (!inMonth(e, month)) {
                continue;
            }
            if (e.type() == EntryType.INCOME) {
                income += e.amountCents();
            } else {
                expense += e.amountCents();
                byCategory.merge(e.category(), e.amountCents(), Long::sum);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("month\t").append(month).append('\n')
                .append("income\t").append(Money.format(income)).append('\n')
                .append("expense\t").append(Money.format(expense)).append('\n')
                .append("balance\t").append(Money.format(income - expense)).append('\n')
                .append("expense_by_category\n");
        byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(c -> sb.append(c.getKey()).append('\t').append(Money.format(c.getValue())).append('\n'));
        return sb.toString();
    }

    /** list の引数：なし、または --month yyyy-MM。それ以外は入力エラー。 */
    private static String parseOptionalMonth(List<String> args) throws LedgerException {
        if (args.isEmpty()) {
            return null;
        }
        if (args.size() == 2 && args.get(0).equals("--month")) {
            return Validators.month(args.get(1));
        }
        throw LedgerException.input("usage: list [--month yyyy-MM]");
    }

    private static boolean inMonth(Entry e, String month) {
        return e.date().toString().startsWith(month + "-");
    }
}
