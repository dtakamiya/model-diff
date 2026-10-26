package ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/**
 * 入力値の検証と変換。違反時は LedgerException.input（終了コード 2）を投げる。
 * CSV 読み込み時の値検証にも再利用する。
 */
public final class Validator {
    private static final Pattern DATE_RE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern MONTH_RE = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");
    private static final Pattern AMOUNT_RE = Pattern.compile("[0-9]{1,12}(\\.[0-9]{1,2})?");
    private static final Pattern ID_RE = Pattern.compile("[1-9][0-9]*");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private Validator() {
    }

    /** yyyy-MM-dd かつ実在する暦日。 */
    public static LocalDate date(String s) {
        if (!DATE_RE.matcher(s).matches()) {
            throw LedgerException.input("invalid date: " + s);
        }
        try {
            return LocalDate.parse(s, DATE_FMT);
        } catch (DateTimeParseException e) {
            throw LedgerException.input("invalid date: " + s);
        }
    }

    /** yyyy-MM（月は 01〜12）。 */
    public static YearMonth month(String s) {
        if (!MONTH_RE.matcher(s).matches()) {
            throw LedgerException.input("invalid month: " + s);
        }
        return YearMonth.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(5, 7)));
    }

    /** income / expense（小文字のみ）。 */
    public static EntryType type(String s) {
        EntryType t = EntryType.fromLabel(s);
        if (t == null) {
            throw LedgerException.input("invalid type: " + s);
        }
        return t;
    }

    /** 1 文字以上、タブ・CR・LF を含まない。trim はしない。 */
    public static String category(String s) {
        if (s.isEmpty() || hasControl(s)) {
            throw LedgerException.input("invalid category");
        }
        return s;
    }

    /** 空可、タブ・CR・LF を含まない。 */
    public static String memo(String s) {
        if (hasControl(s)) {
            throw LedgerException.input("invalid memo");
        }
        return s;
    }

    /** 正の金額。小数 2 桁にそろえて返す。 */
    public static BigDecimal amount(String s) {
        if (!AMOUNT_RE.matcher(s).matches()) {
            throw LedgerException.input("invalid amount: " + s);
        }
        BigDecimal v = new BigDecimal(s);
        if (v.signum() <= 0) {
            throw LedgerException.input("amount must be positive: " + s);
        }
        return Money.normalize(v);
    }

    /** 先頭 0 なしの正整数で Long.MAX_VALUE 以下。 */
    public static long id(String s) {
        if (!ID_RE.matcher(s).matches()) {
            throw LedgerException.input("invalid id: " + s);
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw LedgerException.input("invalid id: " + s);
        }
    }

    private static boolean hasControl(String s) {
        return s.indexOf('\t') >= 0 || s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0;
    }
}
