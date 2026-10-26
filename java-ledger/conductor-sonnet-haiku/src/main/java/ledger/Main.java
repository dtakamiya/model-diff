package ledger;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI 家計簿 {@code ledger} のエントリポイント。
 *
 * <p>正常時は標準出力に結果だけを出し、終了コード 0 で終わる。
 * エラー時は標準出力に何も出さず、標準エラー出力に {@code error: } で始まる 1 行を出して、
 * 例外の種類に応じた終了コードで終わる。
 */
public final class Main {

    static final int EXIT_OK = 0;
    /** ファイル IO エラー（読み書きできない、データが壊れている、保存先ディレクトリが無い等） */
    static final int EXIT_IO_ERROR = 1;
    /** 入力・引数エラー */
    static final int EXIT_INPUT_ERROR = 2;
    /** delete で存在しない ID を指定した */
    static final int EXIT_NOT_FOUND = 3;

    /** --data 無指定時のデータファイル（ホーム直下の .ledger.csv） */
    private static final Path DEFAULT_DATA_PATH =
            Path.of(System.getProperty("user.home"), ".ledger.csv");

    private Main() {
    }

    public static void main(String[] args) {
        // 文字コードは実行環境の既定値に依存させず、UTF-8 を明示する
        PrintStream out = utf8Stream(FileDescriptor.out);
        PrintStream err = utf8Stream(FileDescriptor.err);
        int exitCode = run(args, out, err);
        out.flush();
        err.flush();
        System.exit(exitCode);
    }

    /**
     * 引数を解釈して実行し、終了コードを返す。出力先を引数で受けるのはテストのため。
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            CommandParser.Invocation invocation = CommandParser.parse(args, DEFAULT_DATA_PATH);
            // 出力は成功時にまとめて出す（エラー時は標準出力に何も出さない）
            List<String> lines = Commands.execute(invocation);
            for (String line : lines) {
                out.print(line + "\n");
            }
            return EXIT_OK;
        } catch (InputException e) {
            return reportError(err, EXIT_INPUT_ERROR, e.getMessage());
        } catch (DataFileException e) {
            return reportError(err, EXIT_IO_ERROR, e.getMessage());
        } catch (NotFoundException e) {
            return reportError(err, EXIT_NOT_FOUND, e.getMessage());
        } catch (RuntimeException e) {
            // 想定外の例外も異常終了させず、1 行のエラーとして報告する
            return reportError(err, EXIT_IO_ERROR, "予期しないエラーが発生しました: " + e);
        }
    }

    /** エラーメッセージを 1 行にして標準エラー出力へ出し、終了コードを返す */
    private static int reportError(PrintStream err, int exitCode, String message) {
        // メッセージに改行が混ざっても 1 行に収まるよう空白へ置き換える
        String oneLine = (message == null ? "不明なエラー" : message).replaceAll("[\\r\\n\\t]+", " ");
        err.print("error: " + oneLine + "\n");
        return exitCode;
    }

    private static PrintStream utf8Stream(FileDescriptor descriptor) {
        return new PrintStream(new FileOutputStream(descriptor), false, StandardCharsets.UTF_8);
    }
}
