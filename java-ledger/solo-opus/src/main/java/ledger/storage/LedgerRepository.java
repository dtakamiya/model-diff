package ledger.storage;

import ledger.error.StorageException;
import ledger.model.LedgerData;

/** 家計簿データの永続化を担うインタフェース。 */
public interface LedgerRepository {

    /** 保存済みデータを読み込む。データが無い場合は {@link LedgerData#empty()} を返す。 */
    LedgerData load() throws StorageException;

    /** データ全体を保存する。 */
    void save(LedgerData data) throws StorageException;
}
