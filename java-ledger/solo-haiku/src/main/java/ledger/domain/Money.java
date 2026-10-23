package ledger.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 金額の解析と書式化。金額は銭単位の {@code long} で扱い、浮動小数は使わない。
 */
public final class Money {

    /** 整数部 1〜12 桁、小数部は任意で 1〜2 桁 */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("[0-9]{1,12}(\\.[0-9]{1,2})?");

    private Money() {
    }

    /**
     * 正の金額文字列を銭単位に変換する。{@code 1200}、{@code 1200.5}、{@code 1200.50} を受け付ける。
     *
     * @return 銭単位の金額。形式違い・0 以下なら空
     */
    public static Optional<Long> parseCents(String text) {
        if (!AMOUNT_PATTERN.matcher(text).matches()) {
            return Optional.empty();
        }
        String[] parts = text.split("\\.");
        long yen = Long.parseLong(parts[0]);
        // 小数部は 1 桁なら 0 を補って 2 桁にする（「5」→ 50 銭）
        long sen = parts.length == 2 ? Long.parseLong((parts[1] + "0").substring(0, 2)) : 0;
        long cents = yen * 100 + sen;
        return cents > 0 ? Optional.of(cents) : Optional.empty();
    }

    /**
     * 銭単位の金額を、小数 2 桁・3 桁区切りなしの文字列にする（例: {@code 120050} → {@code 1200.50}）。
     * 負の値は先頭に {@code -} を付ける。
     */
    public static String format(long cents) {
        String sign = cents < 0 ? "-" : "";
        long abs = Math.abs(cents);
        return String.format(Locale.ROOT, "%s%d.%02d", sign, abs / 100, abs % 100);
    }
}
