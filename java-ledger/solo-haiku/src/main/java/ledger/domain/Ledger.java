package ledger.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 家計簿の全データを表す集約。ID の採番もここで行う。
 * ID は削除しても再利用しないため、次に採番する ID（{@code nextId}）を保持する。
 */
public final class Ledger {

    private static final Comparator<Entry> DATE_THEN_ID =
            Comparator.comparing(Entry::date).thenComparingLong(Entry::id);

    /** 次に採番する ID */
    private long nextId;
    private final List<Entry> entries;

    /**
     * 既存データから復元する。
     *
     * @throws IllegalArgumentException ID が重複している、または採番済みの ID 以上を含む場合
     */
    public Ledger(long nextId, List<Entry> entries) {
        Set<Long> seenIds = new HashSet<>();
        for (Entry entry : entries) {
            if (entry.id() >= nextId || !seenIds.add(entry.id())) {
                throw new IllegalArgumentException("ID が不正です: " + entry.id());
            }
        }
        this.nextId = nextId;
        this.entries = new ArrayList<>(entries);
    }

    /** データが無い状態（次の ID は 1） */
    public static Ledger empty() {
        return new Ledger(1, List.of());
    }

    /** 記録を追加して採番した ID を返す */
    public long add(LocalDate date, EntryType type, String category, long amountCents, String memo) {
        long id = nextId;
        nextId++;
        entries.add(new Entry(id, date, type, category, amountCents, memo));
        return id;
    }

    /** 指定 ID の記録を削除する。存在しなければ false（ID は再利用しないので nextId は変えない）。 */
    public boolean delete(long id) {
        return entries.removeIf(entry -> entry.id() == id);
    }

    /**
     * 記録を日付昇順・同日は ID 昇順で返す。月が指定されればその月の分だけ。
     */
    public List<Entry> list(Optional<YearMonth> month) {
        return entries.stream()
                .filter(entry -> month.map(m -> YearMonth.from(entry.date()).equals(m)).orElse(true))
                .sorted(DATE_THEN_ID)
                .toList();
    }

    public Summary summary(YearMonth month) {
        return Summary.of(month, entries);
    }

    /** 保存用に、次に採番する ID を返す */
    public long nextId() {
        return nextId;
    }

    /** 保存用の全記録（登録順）。変更不可のビュー。 */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }
}
