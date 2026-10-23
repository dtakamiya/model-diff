package ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/**
 * 入力値の検証と変換をまとめたクラス。
 * 違反した場合は {@link InputException}（終了コード 2）を投げる。
 * データファイル読込時にも同じルールを使い回し、壊れたデータを検出する。
 */
public final class Validator {

    /** 日付の形式（yyyy-MM-dd）。実在性は別途 STRICT 解決で確認する */
    private static final Pattern DATE_FORMAT = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    /** 日付の解釈。STRICT なので 2026-02-30 のような存在しない日付は弾かれる */
    private static final DateTimeFormatter DATE_PARSER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    /** 金額の形式。整数部 1〜12 桁、小数部は 1〜2 桁まで */
    private static final Pattern AMOUNT_FORMAT = Pattern.compile("[0-9]{1,12}(\\.[0-9]{1,2})?");
    /** 月の形式（yyyy-MM、月は 01〜12） */
    private static final Pattern MONTH_FORMAT = Pattern.compile("[0-9]{4}-(0[1-9]|1[0-2])");
    /** ID の形式（先頭 0 なしの正の整数）。上限は Long.MAX_VALUE を別途確認する */
    private static final Pattern ID_FORMAT = Pattern.compile("[1-9][0-9]*");
    /** カテゴリ：1 文字以上で、タブ・CR・LF を含まない */
    private static final Pattern CATEGORY_FORMAT = Pattern.compile("[^\\t\\r\\n]+");
    /** メモ：空文字可で、タブ・CR・LF を含まない */
    private static final Pattern MEMO_FORMAT = Pattern.compile("[^\\t\\r\\n]*");

    private Validator() {
    }

    /** 日付を検証して {@link LocalDate} にする */
    public static LocalDate parseDate(String value) {
        if (value == null || !DATE_FORMAT.matcher(value).matches()) {
            throw new InputException("日付は yyyy-MM-dd 形式で指定してください: " + value);
        }
        try {
            return LocalDate.parse(value, DATE_PARSER);
        } catch (DateTimeParseException e) {
            throw new InputException("存在しない日付です: " + value);
        }
    }

    /** 種別（小文字の income / expense のみ有効）を検証する */
    public static EntryType parseType(String value) {
        for (EntryType type : EntryType.values()) {
            if (type.label().equals(value)) {
                return type;
            }
        }
        throw new InputException("種別は income か expense を指定してください: " + value);
    }

    /** カテゴリを検証する。前後の空白も含めてそのまま返す（trim しない） */
    public static String parseCategory(String value) {
        if (value == null || !CATEGORY_FORMAT.matcher(value).matches()) {
            throw new InputException("カテゴリは 1 文字以上で、タブ・改行を含めないでください");
        }
        return value;
    }

    /** 金額を検証して {@link BigDecimal} にする。正の数のみ有効 */
    public static BigDecimal parseAmount(String value) {
        if (value == null || !AMOUNT_FORMAT.matcher(value).matches()) {
            throw new InputException("金額は正の数（小数点以下 2 桁まで）で指定してください: " + value);
        }
        BigDecimal amount = new BigDecimal(value);
        if (amount.signum() <= 0) {
            throw new InputException("金額は 0 より大きい値を指定してください: " + value);
        }
        return amount;
    }

    /** メモを検証する。空文字は有効 */
    public static String parseMemo(String value) {
        if (value == null || !MEMO_FORMAT.matcher(value).matches()) {
            throw new InputException("メモにタブ・改行を含めないでください");
        }
        return value;
    }

    /** 月（yyyy-MM）を検証して {@link YearMonth} にする */
    public static YearMonth parseMonth(String value) {
        if (value == null || !MONTH_FORMAT.matcher(value).matches()) {
            throw new InputException("月は yyyy-MM 形式（01〜12）で指定してください: " + value);
        }
        return YearMonth.parse(value);
    }

    /** ID（先頭 0 なしの正の整数、Long.MAX_VALUE 以下）を検証する */
    public static long parseId(String value) {
        if (value == null || !ID_FORMAT.matcher(value).matches()) {
            throw new InputException("ID は 1 以上の整数で指定してください: " + value);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new InputException("ID が大きすぎます: " + value);
        }
    }
}
