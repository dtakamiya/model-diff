package ledger;

/**
 * プロセスの終了コード。仕様で固定された値を持つ。
 */
public enum ExitCode {
    OK(0),
    /** ファイル IO エラー（読み書き不可、データ破損、保存先ディレクトリが無い等） */
    IO_ERROR(1),
    /** 入力・引数エラー */
    USAGE_ERROR(2),
    /** delete で存在しない ID を指定した */
    NOT_FOUND(3);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
