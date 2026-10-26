package ledger;

/**
 * 対象が存在しないエラー（終了コード 3）。delete で存在しない ID を指定した場合に使う。
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
