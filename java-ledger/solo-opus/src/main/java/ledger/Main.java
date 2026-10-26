package ledger;

import ledger.cli.LedgerApp;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * エントリポイント。標準出力・標準エラー出力を UTF-8 に固定し、アプリを実行して終了コードを返す。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // 実行環境の既定文字コードに依存しないよう、UTF-8 の PrintStream を明示的に作る
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        Path defaultData = Path.of(System.getProperty("user.home"), ".ledger.csv");

        int exitCode = new LedgerApp(defaultData).run(args, out, err);
        out.flush();
        err.flush();
        System.exit(exitCode);
    }
}
