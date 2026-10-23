package ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 保存対象のデータ全体：次に採番する ID と全エントリ。 */
public final class LedgerData {
    private long nextId;
    private final List<Entry> entries;

    public LedgerData(long nextId, List<Entry> entries) {
        this.nextId = nextId;
        this.entries = new ArrayList<>(entries);
    }

    public static LedgerData empty() {
        return new LedgerData(1, List.of());
    }

    public long nextId() {
        return nextId;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** ID を採番して追加する。削除しても nextId は戻らないので ID は再利用されない。 */
    public Entry add(LocalDate date, EntryType type, String category, BigDecimal amount, String memo) {
        if (nextId == Long.MAX_VALUE) {
            throw LedgerException.io("id space exhausted", null);
        }
        Entry e = new Entry(nextId++, date, type, category, amount, memo);
        entries.add(e);
        return e;
    }

    /** 指定 ID を削除する。存在しなければ false。 */
    public boolean remove(long id) {
        return entries.removeIf(e -> e.id() == id);
    }
}
