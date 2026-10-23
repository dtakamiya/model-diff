package ledger.service;

import ledger.error.NotFoundException;
import ledger.error.StorageException;
import ledger.model.Entry;
import ledger.model.EntryType;
import ledger.model.LedgerData;
import ledger.storage.LedgerRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 家計簿の業務ロジック（登録・一覧・削除・月次集計）。
 * 入力値の検証は済んでいる前提で、リポジトリを通じてデータを読み書きする。
 */
public final class LedgerService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    /** 一覧の並び順：日付昇順、同日は ID 昇順。 */
    private static final Comparator<Entry> LIST_ORDER =
            Comparator.comparing(Entry::date).thenComparingLong(Entry::id);

    /** カテゴリ別支出の並び順：金額降順、同額はカテゴリ文字列昇順。 */
    private static final Comparator<MonthlySummary.CategoryTotal> CATEGORY_ORDER =
            Comparator.comparing(MonthlySummary.CategoryTotal::amount).reversed()
                    .thenComparing(MonthlySummary.CategoryTotal::category);

    private final LedgerRepository repository;

    public LedgerService(LedgerRepository repository) {
        this.repository = repository;
    }

    /** 1 件登録し、採番した ID を返す。ID は削除済みのものも含めて再利用しない。 */
    public long add(LocalDate date, EntryType type, String category, BigDecimal amount, String memo)
            throws StorageException {
        LedgerData data = repository.load();
        long id = data.nextId();
        if (id == Long.MAX_VALUE) {
            throw new StorageException("no more ids can be assigned");
        }
        List<Entry> entries = new ArrayList<>(data.entries());
        entries.add(new Entry(id, date, type, category, amount, memo));
        repository.save(new LedgerData(id + 1, entries));
        return id;
    }

    /** 全件（月指定時はその月の分）を一覧の並び順で返す。 */
    public List<Entry> list(Optional<YearMonth> month) throws StorageException {
        return repository.load().entries().stream()
                .filter(e -> month.map(m -> YearMonth.from(e.date()).equals(m)).orElse(true))
                .sorted(LIST_ORDER)
                .toList();
    }

    /** 指定 ID を削除する。存在しなければ {@link NotFoundException}（データは変更しない）。 */
    public void delete(long id) throws StorageException, NotFoundException {
        LedgerData data = repository.load();
        List<Entry> remaining = data.entries().stream().filter(e -> e.id() != id).toList();
        if (remaining.size() == data.entries().size()) {
            throw new NotFoundException("no entry with id " + id);
        }
        // next_id は据え置き（削除した ID を再利用しない）
        repository.save(new LedgerData(data.nextId(), remaining));
    }

    /** 指定月の収入・支出・カテゴリ別支出を集計する。 */
    public MonthlySummary summarize(YearMonth month) throws StorageException {
        BigDecimal income = ZERO;
        BigDecimal expense = ZERO;
        Map<String, BigDecimal> byCategory = new TreeMap<>();
        for (Entry e : repository.load().entries()) {
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
        List<MonthlySummary.CategoryTotal> totals = byCategory.entrySet().stream()
                .map(en -> new MonthlySummary.CategoryTotal(en.getKey(), en.getValue()))
                .sorted(CATEGORY_ORDER)
                .toList();
        return new MonthlySummary(month, income, expense, totals);
    }
}
