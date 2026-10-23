package ledger;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** エントリポイント。出力は成功時のみ stdout へ、失敗時は stderr に 1 行。 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), false, StandardCharsets.UTF_8);
        int code = run(args, System.getProperty("user.home") + "/.ledger.csv", out, err);
        out.flush();
        err.flush();
        System.exit(code);
    }

    /** テストしやすいよう、ストリームと既定パスを受け取って終了コードを返す。 */
    static int run(String[] args, String defaultDataPath, PrintStream out, PrintStream err) {
        try {
            // 引数検証 → ファイル処理の順。結果はコマンド完了後にまとめて書き出す
            CommandLine cl = CommandLine.parse(args, defaultDataPath);
            String result = cl.command().execute(new LedgerRepository(cl.dataPath()));
            out.print(result);
            return 0;
        } catch (LedgerException e) {
            err.print("error: " + oneLine(e.getMessage()) + "\n");
            return e.exitCode();
        } catch (RuntimeException e) {
            err.print("error: unexpected failure: " + oneLine(e.getMessage()) + "\n");
            return LedgerException.EXIT_IO;
        }
    }

    /** エラーメッセージを必ず 1 行にする。 */
    private static String oneLine(String s) {
        return String.valueOf(s).replace('\n', ' ').replace('\r', ' ');
    }
}
