package ledger;

/** 入力・引数エラー（終了コード 2）。 */
final class InputException extends RuntimeException {

    InputException(String message) {
        super(message);
    }
}
