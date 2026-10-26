package ledger;

/** 終了コードを伴うエラー。Main で捕捉して標準エラー出力へ書く。 */
public class LedgerException extends Exception {
    /** 終了コード：ファイル IO エラー */
    public static final int EXIT_IO = 1;
    /** 終了コード：入力・引数エラー */
    public static final int EXIT_INPUT = 2;
    /** 終了コード：delete で ID が存在しない */
    public static final int EXIT_NOT_FOUND = 3;

    private final int exitCode;

    public LedgerException(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    public LedgerException(int exitCode, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }

    public static LedgerException input(String message) {
        return new LedgerException(EXIT_INPUT, message);
    }

    public static LedgerException io(String message, Throwable cause) {
        return new LedgerException(EXIT_IO, message, cause);
    }

    public static LedgerException notFound(String message) {
        return new LedgerException(EXIT_NOT_FOUND, message);
    }
}
