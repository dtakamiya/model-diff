package ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 家計簿のユースケース（追加・一覧・削除・集計）。
 * 入力は検証済みの値を受け取り、永続化は {@link LedgerFile} に任せる。
 */
final class LedgerService {

    private static final String INCOME = "income";

    private final LedgerFile file;

    LedgerService(LedgerFile file) {
        this.file = file;
    }

    /** 件数を追加し、採番した ID を返す。ID は next_id から採番し、next_id を 1 進める。 */
    long add(LocalDate date, String type, String category, BigDecimal amount, String memo) {
        LedgerData data = file.load();
        if (data.nextId() == Long.MAX_VALUE) {
            throw new LedgerIOException("ID の上限に達したため追加できません");
        }
        long id = data.nextId();
        List<Entry> entries = new ArrayList<>(data.entries());
        entries.add(new Entry(id, date, type, category, amount, memo));
        file.save(new LedgerData(id + 1, entries));
        return id;
    }

    /** 全件、または指定月の分を日付昇順・同日は ID 昇順で返す。 */
    List<Entry> list(YearMonth month) {
        return file.load().entries().stream()
                .filter(e -> month == null || YearMonth.from(e.date()).equals(month))
                .sorted(Comparator.comparing(Entry::date).thenComparingLong(Entry::id))
                .toList();
    }

    /**
     * 指定 ID の件数を削除する。next_id は変えない（ID は再利用しない）。
     *
     * @throws NotFoundException 該当 ID が無いとき（データは変更しない）
     */
    void delete(long id) {
        LedgerData data = file.load();
        List<Entry> entries = new ArrayList<>(data.entries());
        if (!entries.removeIf(e -> e.id() == id)) {
            throw new NotFoundException("ID " + id + " は存在しません");
        }
        file.save(new LedgerData(data.nextId(), entries));
    }

    /** 指定月の収入・支出・残高と、カテゴリ別の支出合計を集計する。 */
    Summary summary(YearMonth month) {
        BigDecimal income = BigDecimal.ZERO.setScale(2);
        BigDecimal expense = BigDecimal.ZERO.setScale(2);
        Map<String, BigDecimal> expenseByCategory = new HashMap<>();

        for (Entry e : list(month)) {
            if (INCOME.equals(e.type())) {
                income = income.add(e.amount());
            } else {
                expense = expense.add(e.amount());
                expenseByCategory.merge(e.category(), e.amount(), BigDecimal::add);
            }
        }

        List<Summary.CategoryTotal> categories = expenseByCategory.entrySet().stream()
                .map(en -> new Summary.CategoryTotal(en.getKey(), en.getValue()))
                // 金額の降順、同額はカテゴリ名の昇順（String.compareTo 順）
                .sorted((x, y) -> {
                    int byAmount = y.amount().compareTo(x.amount());
                    return byAmount != 0 ? byAmount : x.category().compareTo(y.category());
                })
                .toList();

        return new Summary(month, income, expense, income.subtract(expense), categories);
    }
}
