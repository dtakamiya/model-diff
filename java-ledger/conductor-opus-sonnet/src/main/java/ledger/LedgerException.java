package ledger;

/** 終了コードを伴うアプリケーション例外。 */
public class LedgerException extends RuntimeException {
    public static final int EXIT_IO = 1;
    public static final int EXIT_INPUT = 2;
    public static final int EXIT_NOT_FOUND = 3;

    private final int exitCode;

    public LedgerException(int exitCode, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }

    /** 入力・引数エラー（終了コード 2）。 */
    public static LedgerException input(String message) {
        return new LedgerException(EXIT_INPUT, message, null);
    }

    /** ファイル IO エラー・データ破損（終了コード 1）。 */
    public static LedgerException io(String message, Throwable cause) {
        return new LedgerException(EXIT_IO, message, cause);
    }

    /** 対象 ID が存在しない（終了コード 3）。 */
    public static LedgerException notFound(String message) {
        return new LedgerException(EXIT_NOT_FOUND, message, null);
    }
}
