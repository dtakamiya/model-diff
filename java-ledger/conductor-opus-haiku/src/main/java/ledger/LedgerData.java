package ledger;

import java.util.List;

/**
 * データファイルの内容全体。
 * nextId は次に採番する ID。削除しても巻き戻さないため、ファイルに保存して復元する。
 */
record LedgerData(long nextId, List<Entry> entries) {

    /** ファイル無し・0 バイトのときの状態（次の ID は 1）。 */
    static LedgerData empty() {
        return new LedgerData(1L, List.of());
    }
}
