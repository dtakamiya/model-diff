package ledger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/** 引数を解析・検証して Command を組み立てる。ファイルには触れない。 */
public final class CommandLine {
    private final Path dataPath;
    private final Command command;

    private CommandLine(Path dataPath, Command command) {
        this.dataPath = dataPath;
        this.command = command;
    }

    public Path dataPath() {
        return dataPath;
    }

    public Command command() {
        return command;
    }

    /** 不正な引数は LedgerException.input（終了コード 2）。 */
    public static CommandLine parse(String[] args, String defaultDataPath) {
        int i = 0;
        String data = defaultDataPath;
        // --data は command の前にだけ置ける
        if (args.length > 0 && args[0].equals("--data")) {
            if (args.length < 2) {
                throw LedgerException.input("--data requires a value");
            }
            data = args[1];
            i = 2;
        }
        if (i >= args.length) {
            throw LedgerException.input("no command specified");
        }
        String name = args[i];
        List<String> rest = Arrays.asList(args).subList(i + 1, args.length);
        Command cmd;
        switch (name) {
            case "add" -> cmd = parseAdd(rest);
            case "list" -> cmd = parseList(rest);
            case "delete" -> cmd = parseDelete(rest);
            case "summary" -> cmd = parseSummary(rest);
            default -> throw LedgerException.input("unknown command: " + name);
        }
        return new CommandLine(Paths.get(data), cmd);
    }

    private static Command parseAdd(List<String> a) {
        if (a.size() != 4 && a.size() != 5) {
            throw LedgerException.input("usage: add <yyyy-MM-dd> <income|expense> <category> <amount> [memo]");
        }
        return new AddCommand(Validator.date(a.get(0)), Validator.type(a.get(1)),
                Validator.category(a.get(2)), Validator.amount(a.get(3)),
                Validator.memo(a.size() == 5 ? a.get(4) : ""));
    }

    private static Command parseList(List<String> a) {
        if (a.isEmpty()) {
            return new ListCommand(null);
        }
        if (a.size() == 2 && a.get(0).equals("--month")) {
            return new ListCommand(Validator.month(a.get(1)));
        }
        throw LedgerException.input("usage: list [--month yyyy-MM]");
    }

    private static Command parseDelete(List<String> a) {
        if (a.size() != 1) {
            throw LedgerException.input("usage: delete <id>");
        }
        return new DeleteCommand(Validator.id(a.get(0)));
    }

    private static Command parseSummary(List<String> a) {
        if (a.size() != 2 || !a.get(0).equals("--month")) {
            throw LedgerException.input("usage: summary --month yyyy-MM");
        }
        return new SummaryCommand(Validator.month(a.get(1)));
    }
}
