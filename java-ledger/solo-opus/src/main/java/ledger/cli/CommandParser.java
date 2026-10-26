package ledger.cli;

import ledger.error.InputException;
import ledger.model.FieldParser;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * サブコマンド名と引数を検証し、{@link Command} に変換する。
 * データファイルを読む前に呼ばれ、不正な引数はすべて {@link InputException}（終了コード 2）にする。
 */
final class CommandParser {

    private static final String MONTH_OPTION = "--month";

    private CommandParser() {
    }

    static Command parse(String name, List<String> args) throws InputException {
        // --data はコマンドより前にだけ置ける
        if (args.contains(LedgerApp.DATA_OPTION)) {
            throw new InputException("unknown option after command: " + LedgerApp.DATA_OPTION);
        }
        return switch (name) {
            case "add" -> parseAdd(args);
            case "list" -> new Command.ListEntries(parseOptionalMonth(args));
            case "delete" -> parseDelete(args);
            case "summary" -> parseSummary(args);
            default -> throw new InputException("unknown command: " + name);
        };
    }

    /** {@code add <date> <type> <category> <amount> [memo]} */
    private static Command parseAdd(List<String> args) throws InputException {
        if (args.size() != 4 && args.size() != 5) {
            throw new InputException("usage: add <yyyy-MM-dd> <income|expense> <category> <amount> [memo]");
        }
        return new Command.Add(
                field(FieldParser::parseDate, args.get(0)),
                field(FieldParser::parseType, args.get(1)),
                field(FieldParser::parseCategory, args.get(2)),
                field(FieldParser::parseAmount, args.get(3)),
                field(FieldParser::parseMemo, args.size() == 5 ? args.get(4) : ""));
    }

    /** {@code delete <id>} */
    private static Command parseDelete(List<String> args) throws InputException {
        if (args.size() != 1) {
            throw new InputException("usage: delete <id>");
        }
        return new Command.Delete(field(FieldParser::parseId, args.get(0)));
    }

    /** {@code summary --month yyyy-MM}（--month は必須） */
    private static Command parseSummary(List<String> args) throws InputException {
        return new Command.Summary(parseOptionalMonth(args)
                .orElseThrow(() -> new InputException("usage: summary --month yyyy-MM")));
    }

    /** 引数なし、または {@code --month yyyy-MM} のみを受け付ける。 */
    private static Optional<YearMonth> parseOptionalMonth(List<String> args) throws InputException {
        if (args.isEmpty()) {
            return Optional.empty();
        }
        if (args.size() != 2 || !args.get(0).equals(MONTH_OPTION)) {
            throw new InputException("expected only: --month yyyy-MM");
        }
        return Optional.of(field(FieldParser::parseMonth, args.get(1)));
    }

    /** 項目の検証エラーを入力エラーに変換する。 */
    private static <T> T field(Function<String, T> parser, String text) throws InputException {
        try {
            return parser.apply(text);
        } catch (IllegalArgumentException e) {
            throw new InputException(e.getMessage());
        }
    }
}
