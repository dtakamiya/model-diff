package ledger;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/** エントリポイント。グローバルオプション解釈、コマンド振り分け、終了コード変換を担う。 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), false, StandardCharsets.UTF_8);
        int code = run(args, out, err);
        out.flush();
        err.flush();
        System.exit(code);
    }

    /** テストから呼べるよう出力先を引数で受け取り、終了コードを返す。エラー時は標準出力に何も出さない。 */
    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            out.print(execute(List.of(args)));
            return 0;
        } catch (LedgerException e) {
            err.println("error: " + e.getMessage());
            return e.exitCode();
        }
    }

    private static String execute(List<String> args) throws LedgerException {
        Path dataPath = Path.of(System.getProperty("user.home"), ".ledger.csv");
        int pos = 0;
        if (!args.isEmpty() && args.get(0).equals("--data")) {
            if (args.size() < 2) {
                throw LedgerException.input("--data には値が必要です");
            }
            dataPath = toPath(args.get(1));
            pos = 2;
        }
        if (pos >= args.size()) {
            throw LedgerException.input("コマンドを指定してください (add|list|delete|summary)");
        }

        Commands commands = new Commands(new LedgerRepository(dataPath));
        List<String> rest = args.subList(pos + 1, args.size());
        switch (args.get(pos)) {
            case "add":
                return commands.add(rest);
            case "list":
                return commands.list(rest);
            case "delete":
                return commands.delete(rest);
            case "summary":
                return commands.summary(rest);
            default:
                throw LedgerException.input("未知のコマンドです: " + args.get(pos));
        }
    }

    private static Path toPath(String text) throws LedgerException {
        try {
            return Path.of(text);
        } catch (InvalidPathException e) {
            throw LedgerException.input("--data のパスが不正です: " + text);
        }
    }
}
