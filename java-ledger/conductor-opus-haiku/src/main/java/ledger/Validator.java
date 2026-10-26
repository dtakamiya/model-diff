package ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 入力値の検証とパース。違反時はすべて {@link InputException}（終了コード 2）を投げる。
 * 金額は double/float を使わず {@link BigDecimal} で扱う。
 */
final class Validator {

    // 日付は形（桁数）を正規表現で、暦として実在するかは STRICT モードのパーサで判定する
    private static final Pattern DATE_SHAPE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    // 整数部 1〜12 桁、小数部 1〜2 桁（符号・指数・末尾ドットは不可）
    private static final Pattern AMOUNT = Pattern.compile("[0-9]{1,12}(\\.[0-9]{1,2})?");
    private static final Pattern MONTH = Pattern.compile("([0-9]{4})-(0[1-9]|1[0-2])");
    // 先頭 0 なしの正の整数。long への収まりは別途判定する
    private static final Pattern ID = Pattern.compile("[1-9][0-9]*");

    private Validator() {}

    /** 日付 {@code yyyy-MM-dd}。存在しない日付（2026-02-30 など）も不正。 */
    static LocalDate date(String s) {
        if (!DATE_SHAPE.matcher(s).matches()) {
            throw new InputException("日付は yyyy-MM-dd 形式で指定してください: " + s);
        }
        try {
            return LocalDate.parse(s, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new InputException("実在しない日付です: " + s);
        }
    }

    /** 種別は小文字の income または expense のみ。 */
    static String type(String s) {
        if (!"income".equals(s) && !"expense".equals(s)) {
            throw new InputException("type は income か expense を指定してください: " + s);
        }
        return s;
    }

    /** カテゴリは 1 文字以上。前後の空白は保持し、タブ・改行は不可。 */
    static String category(String s) {
        if (s.isEmpty()) {
            throw new InputException("category は 1 文字以上で指定してください");
        }
        if (containsTabOrLineBreak(s)) {
            throw new InputException("category にタブや改行は使えません");
        }
        return s;
    }

    /** 金額は正の数。小数点以下 2 桁に揃えて返す（例: 1200.5 → 1200.50）。 */
    static BigDecimal amount(String s) {
        if (!AMOUNT.matcher(s).matches()) {
            throw new InputException("金額は正の数（小数点以下 2 桁まで）で指定してください: " + s);
        }
        BigDecimal value = new BigDecimal(s);
        if (value.signum() <= 0) {
            throw new InputException("金額は 0 より大きい必要があります: " + s);
        }
        // 正規表現で小数 2 桁以内に限定しているので、丸めは発生しない
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** メモは空文字可。タブ・改行は不可。 */
    static String memo(String s) {
        if (containsTabOrLineBreak(s)) {
            throw new InputException("memo にタブや改行は使えません");
        }
        return s;
    }

    /** 年月 {@code yyyy-MM}（月は 01〜12）。 */
    static YearMonth month(String s) {
        Matcher m = MONTH.matcher(s);
        if (!m.matches()) {
            throw new InputException("月は yyyy-MM 形式（01〜12）で指定してください: " + s);
        }
        return YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    /** ID は 1 以上で long に収まる整数。 */
    static long id(String s) {
        if (!ID.matcher(s).matches()) {
            throw new InputException("ID は 1 以上の整数で指定してください: " + s);
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new InputException("ID が範囲外です: " + s);
        }
    }

    private static boolean containsTabOrLineBreak(String s) {
        return s.indexOf('\t') >= 0 || s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0;
    }
}
