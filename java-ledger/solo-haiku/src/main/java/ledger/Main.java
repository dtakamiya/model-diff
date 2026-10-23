package ledger;

import ledger.cli.App;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 家計簿 CLI のエントリポイント。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // 出力は環境の既定文字コードに依らず UTF-8 にする
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        int exitCode = new App(out, err).run(args);
        System.exit(exitCode);
    }
}
