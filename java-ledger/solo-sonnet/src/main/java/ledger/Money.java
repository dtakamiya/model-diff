package ledger;

import java.util.regex.Pattern;

/** 銭単位の金額の変換・整形。浮動小数は使わない。 */
public final class Money {
    /** 整数部 1〜12 桁、小数部は任意で 1〜2 桁 */
    private static final Pattern AMOUNT = Pattern.compile("[0-9]{1,12}(\\.[0-9]{1,2})?");

    private Money() {
    }

    /** 金額文字列を銭に変換する。形式不正・0 以下は null。 */
    public static Long parsePositive(String text) {
        if (!AMOUNT.matcher(text).matches()) {
            return null;
        }
        int dot = text.indexOf('.');
        long cents;
        if (dot < 0) {
            cents = Long.parseLong(text) * 100;
        } else {
            String fraction = (text.substring(dot + 1) + "0").substring(0, 2);
            cents = Long.parseLong(text.substring(0, dot)) * 100 + Long.parseLong(fraction);
        }
        return cents > 0 ? cents : null;
    }

    /** 銭を常に小数 2 桁（3 桁区切りなし）で整形する。負数は先頭に - を付ける。 */
    public static String format(long cents) {
        long abs = Math.abs(cents);
        return (cents < 0 ? "-" : "") + (abs / 100) + "." + String.format("%02d", abs % 100);
    }
}
