package ledger.cli;

import ledger.error.InputException;
import ledger.error.LedgerException;
import ledger.service.LedgerService;
import ledger.storage.CsvLedgerRepository;

import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * CLI 全体の流れを組み立てる。
 * <ol>
 *   <li>グローバルオプション {@code --data} とコマンドを解析・検証する（ファイルは読まない）</li>
 *   <li>サービスを通じてコマンドを実行する</li>
 *   <li>成功時は結果を標準出力へ、失敗時は {@code error: } で始まる 1 行を標準エラー出力へ書き、終了コードを返す</li>
 * </ol>
 */
public final class LedgerApp {

    static final String DATA_OPTION = "--data";

    private final Path defaultDataFile;

    public LedgerApp(Path defaultDataFile) {
        this.defaultDataFile = defaultDataFile;
    }

    /** コマンドを実行して終了コードを返す。 */
    public int run(String[] args, PrintStream out, PrintStream err) {
        try {
            out.print(execute(Arrays.asList(args)));
            return 0;
        } catch (LedgerException e) {
            err.println("error: " + oneLine(e.getMessage()));
            return e.exitCode();
        } catch (RuntimeException e) {
            // 想定外の不具合も 1 行のエラーとして報告する
            err.println("error: unexpected failure: " + oneLine(String.valueOf(e)));
            return 1;
        }
    }

    private String execute(List<String> args) throws LedgerException {
        Path dataFile = defaultDataFile;
        int pos = 0;
        if (pos < args.size() && args.get(pos).equals(DATA_OPTION)) {
            if (pos + 1 >= args.size()) {
                throw new InputException("missing value for " + DATA_OPTION);
            }
            dataFile = toPath(args.get(pos + 1));
            pos += 2;
        }
        if (pos >= args.size()) {
            throw new InputException("no command given (add, list, delete, summary)");
        }

        // 引数の検証をデータファイルの読み込みより先に行う
        Command command = CommandParser.parse(args.get(pos), args.subList(pos + 1, args.size()));
        return command.execute(new LedgerService(new CsvLedgerRepository(dataFile)));
    }

    private static Path toPath(String text) throws InputException {
        if (text.isEmpty()) {
            throw new InputException("empty value for " + DATA_OPTION);
        }
        try {
            return Path.of(text);
        } catch (InvalidPathException e) {
            throw new InputException("invalid path for " + DATA_OPTION + ": " + e.getMessage());
        }
    }

    /** エラーメッセージを必ず 1 行に収める。 */
    private static String oneLine(String message) {
        return message == null ? "" : message.replaceAll("[\\r\\n]+", " ");
    }
}
