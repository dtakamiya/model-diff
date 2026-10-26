package ledger.cli;

import ledger.ExitCode;
import ledger.LedgerException;
import ledger.domain.EntryType;
import ledger.domain.FieldRules;
import ledger.domain.Money;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/**
 * コマンドライン引数の検証。不正な値は {@link ExitCode#USAGE_ERROR} の例外にする。
 * 形式ルールそのものは {@link FieldRules} と {@link Money} に任せる。
 */
final class InputParser {

    private InputParser() {
    }

    static LocalDate date(String text) {
        return require(FieldRules.date(text), "日付は yyyy-MM-dd の実在する日付で指定してください");
    }

    static YearMonth month(String text) {
        return require(FieldRules.month(text), "月は yyyy-MM（月は 01〜12）で指定してください");
    }

    static EntryType type(String text) {
        return require(FieldRules.type(text), "種別は income か expense を小文字で指定してください");
    }

    static String category(String text) {
        if (!FieldRules.isValidCategory(text)) {
            throw usage("カテゴリは 1 文字以上で、タブや改行を含まないように指定してください");
        }
        return text;
    }

    static String memo(String text) {
        if (!FieldRules.isPlainText(text)) {
            throw usage("メモにタブや改行を含めることはできません");
        }
        return text;
    }

    /** 正の金額を銭単位で返す */
    static long amount(String text) {
        return require(Money.parseCents(text), "金額は 0 より大きい数（整数部 12 桁以内、小数 2 桁以内）で指定してください");
    }

    static long id(String text) {
        return require(FieldRules.id(text), "ID は 1 以上の整数で指定してください");
    }

    private static <T> T require(Optional<T> value, String message) {
        return value.orElseThrow(() -> usage(message));
    }

    static LedgerException usage(String message) {
        return new LedgerException(ExitCode.USAGE_ERROR, message);
    }
}
