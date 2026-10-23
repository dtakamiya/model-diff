package ledger;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

/**
 * CLI の処理本体。
 *
 * <p>役割は三つ：グローバルオプション {@code --data} の解析、コマンドの振り分け、
 * 例外から終了コードへの変換。引数の検証はデータファイルを読む前に行う。
 * 出力は成功が確定してからまとめて書き出し、エラー時は標準出力に何も出さない。
 */
final class App {

    static final int EXIT_OK = 0;
    static final int EXIT_IO_ERROR = 1;
    static final int EXIT_INPUT_ERROR = 2;
    static final int EXIT_NOT_FOUND = 3;

    private static final String OPT_DATA = "--data";
    private static final String OPT_MONTH = "--month";
    private static final String DEFAULT_FILE_NAME = ".ledger.csv";

    private App() {}

    /** 引数を処理して終了コードを返す。出力先は引数で受け取る（テスト可能にするため）。 */
    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            String output = execute(args);
            out.print(output);
            return EXIT_OK;
        } catch (InputException e) {
            return fail(err, e, EXIT_INPUT_ERROR);
        } catch (NotFoundException e) {
            return fail(err, e, EXIT_NOT_FOUND);
        } catch (LedgerIOException e) {
            return fail(err, e, EXIT_IO_ERROR);
        }
    }

    /** エラーを stderr に 1 行で出し、終了コードを返す。 */
    private static int fail(PrintStream err, RuntimeException e, int exitCode) {
        // 入力値に改行が含まれても 1 行に収まるよう置換する
        String message = String.valueOf(e.getMessage()).replaceAll("[\\r\\n]+", " ");
        err.print("error: " + message + "\n");
        return exitCode;
    }

    /** コマンドを実行し、標準出力に出す文字列を組み立てて返す。 */
    private static String execute(String[] args) {
        // --data の値は文字列のまま持ち、Path への変換は引数検証の後（service 生成時）に行う
        String dataArg = null;

        // グローバルオプションは command の前にだけ置ける
        int i = 0;
        while (i < args.length && args[i].startsWith("--")) {
            if (!OPT_DATA.equals(args[i])) {
                throw new InputException("未知のオプションです: " + args[i]);
            }
            if (i + 1 >= args.length) {
                throw new InputException("--data には保存先パスを指定してください");
            }
            dataArg = args[i + 1];
            i += 2;
        }
        if (i >= args.length) {
            throw new InputException("コマンドを指定してください（add / list / delete / summary）");
        }

        String command = args[i];
        String[] rest = Arrays.copyOfRange(args, i + 1, args.length);
        if (Arrays.asList(rest).contains(OPT_DATA)) {
            throw new InputException("--data は command の前に指定してください");
        }
        return switch (command) {
            case "add" -> add(rest, dataArg);
            case "list" -> list(rest, dataArg);
            case "delete" -> delete(rest, dataArg);
            case "summary" -> summary(rest, dataArg);
            default -> throw new InputException("未知のコマンドです: " + command);
        };
    }

    /** add &lt;date&gt; &lt;type&gt; &lt;category&gt; &lt;amount&gt; [memo] */
    private static String add(String[] a, String dataArg) {
        if (a.length != 4 && a.length != 5) {
            throw new InputException("add の引数は 4 個または 5 個です（date type category amount [memo]）");
        }
        // 検証はすべてファイルを読む前に終わらせる
        LocalDate date = Validator.date(a[0]);
        String type = Validator.type(a[1]);
        String category = Validator.category(a[2]);
        BigDecimal amount = Validator.amount(a[3]);
        String memo = a.length == 5 ? Validator.memo(a[4]) : "";

        long id = service(dataArg).add(date, type, category, amount, memo);
        return id + "\n";
    }

    /** list [--month yyyy-MM] */
    private static String list(String[] a, String dataArg) {
        YearMonth month = null;
        if (a.length == 2 && OPT_MONTH.equals(a[0])) {
            month = Validator.month(a[1]);
        } else if (a.length != 0) {
            throw new InputException("list の引数は --month yyyy-MM のみ指定できます");
        }
        return OutputFormatter.list(service(dataArg).list(month));
    }

    /** delete &lt;id&gt; */
    private static String delete(String[] a, String dataArg) {
        if (a.length != 1) {
            throw new InputException("delete の引数は ID を 1 個指定してください");
        }
        long id = Validator.id(a[0]);
        service(dataArg).delete(id);
        return "deleted " + id + "\n";
    }

    /** summary --month yyyy-MM */
    private static String summary(String[] a, String dataArg) {
        if (a.length != 2 || !OPT_MONTH.equals(a[0])) {
            throw new InputException("summary は --month yyyy-MM の指定が必須です");
        }
        YearMonth month = Validator.month(a[1]);
        return OutputFormatter.summary(service(dataArg).summary(month));
    }

    /** dataArg は --data の値（未指定なら null）。不正なパス文字列は IO エラーにする。 */
    private static LedgerService service(String dataArg) {
        return new LedgerService(new LedgerFile(toPath(dataArg)));
    }

    private static Path toPath(String dataArg) {
        if (dataArg == null) {
            return Path.of(System.getProperty("user.home"), DEFAULT_FILE_NAME);
        }
        try {
            return Path.of(dataArg);
        } catch (InvalidPathException e) {
            // NUL 文字などはパスとして扱えない。入力値そのものは出さず、理由だけ伝える
            throw new LedgerIOException("保存先パスが不正です: " + e.getReason(), e);
        }
    }
}
