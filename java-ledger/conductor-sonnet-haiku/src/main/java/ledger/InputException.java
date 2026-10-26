package ledger;

/**
 * 入力・引数エラー（終了コード 2）。
 * 引数の過不足、コマンド未指定・未知、各項目の検証違反などに使う。
 */
public class InputException extends RuntimeException {

    public InputException(String message) {
        super(message);
    }
}
