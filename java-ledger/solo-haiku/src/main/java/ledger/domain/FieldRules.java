package ledger.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 各項目の形式・値のルール。CLI の入力検証と CSV の読み込み検証で共通に使う。
 * 不正な値は例外ではなく空の {@link Optional} で返し、呼び出し側が終了コードを決める。
 */
public final class FieldRules {

    private static final Pattern DATE_PATTERN = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern MONTH_PATTERN = Pattern.compile("[0-9]{4}-[0-9]{2}");
    private static final Pattern ID_PATTERN = Pattern.compile("[1-9][0-9]*");

    // STRICT にして 2026-02-30 のような存在しない日付を弾く
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM").withResolverStyle(ResolverStyle.STRICT);

    private FieldRules() {
    }

    /** {@code yyyy-MM-dd} かつ実在する暦日 */
    public static Optional<LocalDate> date(String text) {
        if (!DATE_PATTERN.matcher(text).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(text, DATE_FORMAT));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** {@code yyyy-MM}（月は 01〜12） */
    public static Optional<YearMonth> month(String text) {
        if (!MONTH_PATTERN.matcher(text).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(YearMonth.parse(text, MONTH_FORMAT));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** 先頭 0 なしの正の整数で、{@code long} の範囲内 */
    public static Optional<Long> id(String text) {
        if (!ID_PATTERN.matcher(text).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** {@code income} または {@code expense} のみ（小文字） */
    public static Optional<EntryType> type(String text) {
        for (EntryType type : EntryType.values()) {
            if (type.word().equals(text)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** 1 文字以上で、タブ・CR・LF を含まないカテゴリ */
    public static boolean isValidCategory(String text) {
        return !text.isEmpty() && isPlainText(text);
    }

    /** タブ・CR・LF を含まないテキスト（メモ・カテゴリ共通の制約） */
    public static boolean isPlainText(String text) {
        return text.indexOf('\t') < 0 && text.indexOf('\r') < 0 && text.indexOf('\n') < 0;
    }
}
