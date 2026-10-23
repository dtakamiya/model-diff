package ledger.error;

/** 指定した ID のデータが存在しない（終了コード 3）。 */
public final class NotFoundException extends LedgerException {

    public NotFoundException(String message) {
        super(message);
    }

    @Override
    public int exitCode() {
        return 3;
    }
}
