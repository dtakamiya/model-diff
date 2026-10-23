package ledger.model;

import java.util.List;

/**
 * データファイル 1 つ分の内容。削除済み ID を再利用しないため、次に採番する ID も保持する。
 *
 * @param nextId  次に採番する ID（1 以上）
 * @param entries 記録（ファイル上の並び順）
 */
public record LedgerData(long nextId, List<Entry> entries) {

    public LedgerData {
        entries = List.copyOf(entries);
    }

    /** データが無い状態（次の ID は 1）。 */
    public static LedgerData empty() {
        return new LedgerData(1, List.of());
    }
}
