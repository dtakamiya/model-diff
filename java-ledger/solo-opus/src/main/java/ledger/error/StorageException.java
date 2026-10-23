package ledger.error;

/** データファイルの読み書き失敗・破損（終了コード 1）。 */
public final class StorageException extends LedgerException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public int exitCode() {
        return 1;
    }
}
