package ledger;

/** ファイル IO エラー、データファイル破損（終了コード 1）。 */
final class LedgerIOException extends RuntimeException {

    LedgerIOException(String message) {
        super(message);
    }

    LedgerIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
