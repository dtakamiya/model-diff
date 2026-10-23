package ledger;

/**
 * データファイルの IO エラー（終了コード 1）。
 * 読み書きできない、形式が壊れている、保存先ディレクトリが無い、ディレクトリを指している等。
 */
public class DataFileException extends RuntimeException {

    public DataFileException(String message) {
        super(message);
    }

    public DataFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
