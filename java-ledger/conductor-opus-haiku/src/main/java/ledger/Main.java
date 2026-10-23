package ledger;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * エントリポイント。標準出力・標準エラーを UTF-8 で明示的に作り、
 * 処理結果の終了コードで JVM を終了する。
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        // 実行環境の既定文字コードに依存しないよう、UTF-8 の PrintStream を直接作る
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        int exitCode = App.run(args, out, err);
        out.flush();
        err.flush();
        System.exit(exitCode);
    }
}
