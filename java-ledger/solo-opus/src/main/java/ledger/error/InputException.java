package ledger.error;

/** 入力・引数エラー（終了コード 2）。 */
public final class InputException extends LedgerException {

    public InputException(String message) {
        super(message);
    }

    @Override
    public int exitCode() {
        return 2;
    }
}
