package ledger;

/**
 * 利用者に見せるエラー。メッセージは標準エラー出力に「error: 」付きで出力され、
 * 終了コードは {@link ExitCode} で決まる。
 */
public class LedgerException extends RuntimeException {

    private final ExitCode exitCode;

    public LedgerException(ExitCode exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    public ExitCode exitCode() {
        return exitCode;
    }
}
