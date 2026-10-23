package ledger.model;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.regex.Pattern;

/**
 * 各項目の文字列を検証して値に変換する。
 * CLI 引数の検証とデータファイル読み込み時の検証の両方で共有する。
 * 不正な値は {@link IllegalArgumentException} で通知し、呼び出し側が適切なエラー種別に変換する。
 */
public final class FieldParser {

    private static final Pattern DATE = Pattern.compile("([0-9]{4})-([0-9]{2})-([0-9]{2})");
    private static final Pattern MONTH = Pattern.compile("([0-9]{4})-(0[1-9]|1[0-2])");
    private static final Pattern AMOUNT = Pattern.compile("[0-9]{1,12}(\\.[0-9]{1,2})?");
    private static final Pattern ID = Pattern.compile("[1-9][0-9]*");

    private FieldParser() {
    }

    /** {@code yyyy-MM-dd} 形式かつ実在する暦日を解析する。 */
    public static LocalDate parseDate(String text) {
        var m = DATE.matcher(text);
        if (!m.matches()) {
            throw new IllegalArgumentException("invalid date (expected yyyy-MM-dd): " + text);
        }
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)));
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("no such date: " + text, e);
        }
    }

    /** {@code yyyy-MM}（月は 01〜12）を解析する。 */
    public static YearMonth parseMonth(String text) {
        var m = MONTH.matcher(text);
        if (!m.matches()) {
            throw new IllegalArgumentException("invalid month (expected yyyy-MM): " + text);
        }
        return YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    /** 小文字の {@code income} / {@code expense} を解析する。 */
    public static EntryType parseType(String text) {
        return EntryType.fromLabel(text)
                .orElseThrow(() -> new IllegalArgumentException("invalid type (expected income or expense): " + text));
    }

    /** カテゴリを検証する。1 文字以上でタブ・CR・LF を含まないこと。値は加工せずそのまま返す。 */
    public static String parseCategory(String text) {
        if (text.isEmpty()) {
            throw new IllegalArgumentException("category must not be empty");
        }
        if (containsControl(text)) {
            throw new IllegalArgumentException("category must not contain tab, CR or LF");
        }
        return text;
    }

    /** メモを検証する。空文字可、タブ・CR・LF を含まないこと。 */
    public static String parseMemo(String text) {
        if (containsControl(text)) {
            throw new IllegalArgumentException("memo must not contain tab, CR or LF");
        }
        return text;
    }

    /** 正の金額（整数部 1〜12 桁、小数部 0〜2 桁）を解析し、スケール 2 の値で返す。 */
    public static BigDecimal parseAmount(String text) {
        if (!AMOUNT.matcher(text).matches()) {
            throw new IllegalArgumentException("invalid amount: " + text);
        }
        BigDecimal value = new BigDecimal(text).setScale(2);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0: " + text);
        }
        return value;
    }

    /** 先頭 0 なしの正の整数 ID（long の範囲内）を解析する。 */
    public static long parseId(String text) {
        if (!ID.matcher(text).matches()) {
            throw new IllegalArgumentException("invalid id: " + text);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("id is too large: " + text, e);
        }
    }

    private static boolean containsControl(String text) {
        return text.indexOf('\t') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0;
    }
}
