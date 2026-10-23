package ledger;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** コマンド引数の検証。違反は終了コード 2 の LedgerException。 */
public final class Validators {
    private static final Pattern DATE = Pattern.compile("([0-9]{4})-([0-9]{2})-([0-9]{2})");
    private static final Pattern MONTH = Pattern.compile("[0-9]{4}-(0[1-9]|1[0-2])");
    private static final Pattern ID = Pattern.compile("[1-9][0-9]*");

    private Validators() {
    }

    public static LocalDate date(String text) throws LedgerException {
        Matcher m = DATE.matcher(text);
        if (m.matches()) {
            try {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
            } catch (DateTimeException ignored) {
                // 実在しない日付は下で入力エラーにする
            }
        }
        throw LedgerException.input("日付は実在する yyyy-MM-dd で指定してください: " + text);
    }

    public static EntryType type(String text) throws LedgerException {
        EntryType type = EntryType.fromLabel(text);
        if (type == null) {
            throw LedgerException.input("type は income か expense を指定してください: " + text);
        }
        return type;
    }

    public static String category(String text) throws LedgerException {
        if (text.isEmpty() || hasTabOrLineBreak(text)) {
            throw LedgerException.input("category は 1 文字以上で、タブ・改行を含められません");
        }
        return text;
    }

    public static long amount(String text) throws LedgerException {
        Long cents = Money.parsePositive(text);
        if (cents == null) {
            throw LedgerException.input("amount は 0 より大きい小数 2 桁以内の数値で指定してください: " + text);
        }
        return cents;
    }

    public static String memo(String text) throws LedgerException {
        if (hasTabOrLineBreak(text)) {
            throw LedgerException.input("memo にタブ・改行は含められません");
        }
        return text;
    }

    /** yyyy-MM を検証してそのまま返す。 */
    public static String month(String text) throws LedgerException {
        if (!MONTH.matcher(text).matches()) {
            throw LedgerException.input("month は yyyy-MM で指定してください: " + text);
        }
        return text;
    }

    public static long id(String text) throws LedgerException {
        if (ID.matcher(text).matches()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // Long の範囲超過は下で入力エラーにする
            }
        }
        throw LedgerException.input("id は正の整数で指定してください: " + text);
    }

    private static boolean hasTabOrLineBreak(String text) {
        return text.indexOf('\t') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0;
    }
}
