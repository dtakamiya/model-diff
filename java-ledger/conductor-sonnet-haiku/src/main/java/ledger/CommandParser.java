package ledger;

import java.math.BigDecimal;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * コマンドライン引数を解析し、検証済みの {@link Command} を作るクラス。
 *
 * <p>書式：{@code ledger [--data <path>] <command> [args...]}
 * <ul>
 *   <li>{@code --data} はコマンドの前にだけ置ける。コマンドの後ろに現れたら入力エラー</li>
 *   <li>引数の検証はすべてここで行い、データファイルはまだ読まない</li>
 * </ul>
 * 不正な引数はすべて {@link InputException}（終了コード 2）になる。
 */
public final class CommandParser {

    private static final String DATA_OPTION = "--data";
    private static final String MONTH_OPTION = "--month";

    private CommandParser() {
    }

    /**
     * 解析結果：データファイルの場所と、実行するコマンド。
     *
     * @param dataPath データファイルのパス
     * @param command  検証済みのコマンド
     */
    public record Invocation(Path dataPath, Command command) {
    }

    /**
     * 引数を解析する。
     *
     * @param args            コマンドライン引数
     * @param defaultDataPath --data 無指定時のデータファイル
     */
    public static Invocation parse(String[] args, Path defaultDataPath) {
        Path dataPath = defaultDataPath;
        boolean dataGiven = false;

        // コマンドの前にあるグローバルオプションを読む
        int i = 0;
        while (i < args.length && args[i].startsWith("-")) {
            if (!DATA_OPTION.equals(args[i])) {
                throw new InputException("未知のオプションです: " + args[i]);
            }
            if (dataGiven) {
                throw new InputException("--data は 1 回だけ指定してください");
            }
            if (i + 1 >= args.length) {
                throw new InputException("--data には保存先のパスを指定してください");
            }
            dataPath = toPath(args[i + 1]);
            dataGiven = true;
            i += 2;
        }

        if (i >= args.length) {
            throw new InputException("コマンドを指定してください（add / list / delete / summary）");
        }
        String name = args[i];
        List<String> rest = Arrays.asList(args).subList(i + 1, args.length);

        Command command = switch (name) {
            case "add" -> parseAdd(rest);
            case "list" -> parseList(rest);
            case "delete" -> parseDelete(rest);
            case "summary" -> parseSummary(rest);
            default -> throw new InputException("未知のコマンドです: " + name);
        };
        return new Invocation(dataPath, command);
    }

    /** add <yyyy-MM-dd> <income|expense> <category> <amount> [memo] */
    private static Command parseAdd(List<String> rest) {
        if (rest.size() != 4 && rest.size() != 5) {
            throw new InputException("add の引数は 4 個または 5 個です（使い方: add <date> <type> <category> <amount> [memo]）");
        }
        // add の位置引数は値として検証するが、--data が後ろに来た場合は未知のオプションとして扱う
        if (rest.contains(DATA_OPTION)) {
            throw new InputException("--data はコマンドの前に指定してください");
        }
        LocalDate date = Validator.parseDate(rest.get(0));
        EntryType type = Validator.parseType(rest.get(1));
        String category = Validator.parseCategory(rest.get(2));
        BigDecimal amount = Validator.parseAmount(rest.get(3));
        // memo は省略可。省略時と "" は同じく空文字として扱う
        String memo = rest.size() == 5 ? Validator.parseMemo(rest.get(4)) : "";
        return new Command.Add(date, type, category, amount, memo);
    }

    /** list [--month yyyy-MM] */
    private static Command parseList(List<String> rest) {
        if (rest.isEmpty()) {
            return new Command.ListEntries(Optional.empty());
        }
        if (rest.size() == 2 && MONTH_OPTION.equals(rest.get(0))) {
            return new Command.ListEntries(Optional.of(Validator.parseMonth(rest.get(1))));
        }
        throw new InputException("list の引数が不正です（使い方: list [--month yyyy-MM]）");
    }

    /** delete <id> */
    private static Command parseDelete(List<String> rest) {
        if (rest.size() != 1) {
            throw new InputException("delete の引数は ID を 1 個だけ指定してください（使い方: delete <id>）");
        }
        return new Command.Delete(Validator.parseId(rest.get(0)));
    }

    /** summary --month yyyy-MM（--month は必須） */
    private static Command parseSummary(List<String> rest) {
        if (rest.size() == 2 && MONTH_OPTION.equals(rest.get(0))) {
            return new Command.ShowSummary(Validator.parseMonth(rest.get(1)));
        }
        throw new InputException("summary は --month yyyy-MM の指定が必要です（使い方: summary --month yyyy-MM）");
    }

    /** データファイルのパス文字列を Path にする。使えない文字列は入力エラー */
    private static Path toPath(String value) {
        try {
            return Path.of(value);
        } catch (InvalidPathException e) {
            throw new InputException("--data のパスが不正です: " + value);
        }
    }
}
