package ledger.error;

/**
 * 終了コードを伴うアプリケーション例外の基底クラス。
 * CLI 層はこの例外を捕まえて「error: メッセージ」を出力し、{@link #exitCode()} で終了する。
 */
public abstract class LedgerException extends Exception {

    protected LedgerException(String message) {
        super(message);
    }

    protected LedgerException(String message, Throwable cause) {
        super(message, cause);
    }

    /** このエラーに対応するプロセスの終了コード。 */
    public abstract int exitCode();
}
