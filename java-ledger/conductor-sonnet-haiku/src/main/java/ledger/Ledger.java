package ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 家計簿の状態（次に採番する ID と全レコード）を保持するドメインクラス。
 * ファイル入出力は行わない。ID は削除しても再利用しないため、次の ID を別に持つ。
 */
public final class Ledger {

    /** 一覧の並び順：日付昇順、同日は ID 昇順 */
    private static final Comparator<Entry> LIST_ORDER =
            Comparator.comparing(Entry::date).thenComparingLong(Entry::id);

    private long nextId;
    private final List<Entry> entries;

    /**
     * @param nextId  次に採番する ID
     * @param entries 既存のレコード
     */
    public Ledger(long nextId, List<Entry> entries) {
        this.nextId = nextId;
        this.entries = new ArrayList<>(entries);
    }

    /** データなしの状態（次の ID は 1） */
    public static Ledger empty() {
        return new Ledger(1, List.of());
    }

    /**
     * レコードを追加し、採番した ID を返す。
     * 金額は小数 2 桁に揃えて保持する。
     */
    public long add(LocalDate date, EntryType type, String category, BigDecimal amount, String memo) {
        if (nextId == Long.MAX_VALUE) {
            throw new DataFileException("ID が上限に達したため追加できません");
        }
        long id = nextId;
        nextId++;
        entries.add(new Entry(id, date, type, category, amount.setScale(2), memo));
        return id;
    }

    /** 指定 ID のレコードを削除する。存在して削除できたら true。ID は再利用しない */
    public boolean remove(long id) {
        return entries.removeIf(entry -> entry.id() == id);
    }

    /**
     * 一覧用のレコードを返す。month が指定されていればその月だけに絞る。
     * 並びは日付昇順、同日は ID 昇順。
     */
    public List<Entry> listSorted(Optional<YearMonth> month) {
        return entries.stream()
                .filter(entry -> month.map(m -> YearMonth.from(entry.date()).equals(m)).orElse(true))
                .sorted(LIST_ORDER)
                .toList();
    }

    /** 次に採番される ID（保存用） */
    public long nextId() {
        return nextId;
    }

    /** 全レコードの読み取り専用ビュー（保存用・集計用） */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }
}
