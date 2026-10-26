package ledger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 全記録と次に採番する ID を保持するドメインモデル。 */
public final class Ledger {
    private final List<Entry> entries;
    private long nextId;

    public Ledger(List<Entry> entries, long nextId) {
        this.entries = new ArrayList<>(entries);
        this.nextId = nextId;
    }

    public static Ledger empty() {
        return new Ledger(List.of(), 1);
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public long nextId() {
        return nextId;
    }

    /** 記録を追加し、採番した ID を返す。ID は削除しても再利用しない。 */
    public long add(LocalDate date, EntryType type, String category, long amountCents, String memo) {
        long id = nextId++;
        entries.add(new Entry(id, date, type, category, amountCents, memo));
        return id;
    }

    /** 指定 ID の記録を削除する。存在したら true。 */
    public boolean delete(long id) {
        return entries.removeIf(e -> e.id() == id);
    }
}
