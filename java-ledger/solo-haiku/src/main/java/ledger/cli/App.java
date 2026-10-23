package ledger.cli;

import ledger.ExitCode;
import ledger.LedgerException;
import ledger.domain.EntryType;
import ledger.domain.Ledger;
import ledger.storage.CsvLedgerStore;

import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CLI の入口。引数を解釈して 1 コマンドを実行し、出力と終了コードを決める。
 * <p>
 * 引数の検証はデータファイルを読む前に済ませ、正常終了時だけ標準出力に書く。
 */
public final class App {

    private static final Path DEFAULT_DATA_PATH = Path.of(System.getProperty("user.home"), ".ledger.csv");
    private static final String DATA_OPTION = "--data";
    private static final String MONTH_OPTION = "--month";

    private final PrintStream out;
    private final PrintStream err;
    private final CsvLedgerStore store = new CsvLedgerStore();

    public App(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * コマンドを実行し、終了コードを返す。
     */
    public int run(String... args) {
        try {
            String output = execute(args);
            out.print(output);
            return ExitCode.OK.code();
        } catch (LedgerException e) {
            err.println("error: " + e.getMessage());
            return e.exitCode().code();
        } catch (RuntimeException e) {
            // 想定外の不具合でも、標準エラー出力の形式と終了コード 1 は守る
            err.println("error: 予期しないエラーが発生しました: " + e);
            return ExitCode.IO_ERROR.code();
        }
    }

    private String execute(String... args) {
        List<String> rest = new ArrayList<>(List.of(args));
        Path dataPath = DEFAULT_DATA_PATH;
        // グローバルオプション --data はコマンドの前にだけ置ける
        if (!rest.isEmpty() && DATA_OPTION.equals(rest.get(0))) {
            if (rest.size() < 2) {
                throw InputParser.usage("--data の値を指定してください");
            }
            dataPath = toPath(rest.get(1));
            rest.subList(0, 2).clear();
        }
        if (rest.isEmpty()) {
            throw InputParser.usage("コマンドを指定してください（add / list / delete / summary）");
        }
        String command = rest.remove(0);
        if (rest.contains(DATA_OPTION)) {
            throw InputParser.usage("--data はコマンドの前に指定してください");
        }
        return switch (command) {
            case "add" -> add(dataPath, rest);
            case "list" -> list(dataPath, rest);
            case "delete" -> delete(dataPath, rest);
            case "summary" -> summary(dataPath, rest);
            default -> throw InputParser.usage("未知のコマンドです: " + command);
        };
    }

    /** add &lt;date&gt; &lt;type&gt; &lt;category&gt; &lt;amount&gt; [memo] */
    private String add(Path dataPath, List<String> args) {
        if (args.size() != 4 && args.size() != 5) {
            throw InputParser.usage("add は 4 個か 5 個の引数が必要です");
        }
        LocalDate date = InputParser.date(args.get(0));
        EntryType type = InputParser.type(args.get(1));
        String category = InputParser.category(args.get(2));
        long amountCents = InputParser.amount(args.get(3));
        String memo = args.size() == 5 ? InputParser.memo(args.get(4)) : "";

        Ledger ledger = store.load(dataPath);
        long id = ledger.add(date, type, category, amountCents, memo);
        store.save(dataPath, ledger);
        return id + "\n";
    }

    /** list [--month yyyy-MM] */
    private String list(Path dataPath, List<String> args) {
        Optional<YearMonth> month = monthOption(args, false);
        Ledger ledger = store.load(dataPath);
        return Report.list(ledger.list(month));
    }

    /** delete &lt;id&gt; */
    private String delete(Path dataPath, List<String> args) {
        if (args.size() != 1) {
            throw InputParser.usage("delete は ID を 1 個指定してください");
        }
        long id = InputParser.id(args.get(0));

        Ledger ledger = store.load(dataPath);
        if (!ledger.delete(id)) {
            throw new LedgerException(ExitCode.NOT_FOUND, "ID " + id + " の記録は存在しません");
        }
        store.save(dataPath, ledger);
        return "deleted " + id + "\n";
    }

    /** summary --month yyyy-MM */
    private String summary(Path dataPath, List<String> args) {
        Optional<YearMonth> month = monthOption(args, true);
        Ledger ledger = store.load(dataPath);
        return Report.summary(ledger.summary(month.orElseThrow()));
    }

    /**
     * {@code --month yyyy-MM} の解釈。引数が無ければ空（required が false の場合のみ）。
     * {@code --month} 以外の引数や値の欠落は入力エラー。
     */
    private static Optional<YearMonth> monthOption(List<String> args, boolean required) {
        if (args.isEmpty() && !required) {
            return Optional.empty();
        }
        if (args.size() != 2 || !MONTH_OPTION.equals(args.get(0))) {
            throw InputParser.usage(required
                    ? "summary は --month yyyy-MM を指定してください"
                    : "list の引数は省略するか --month yyyy-MM のみです");
        }
        return Optional.of(InputParser.month(args.get(1)));
    }

    private static Path toPath(String text) {
        try {
            return Path.of(text);
        } catch (InvalidPathException e) {
            throw new LedgerException(ExitCode.IO_ERROR, "データファイルのパスが不正です");
        }
    }
}
