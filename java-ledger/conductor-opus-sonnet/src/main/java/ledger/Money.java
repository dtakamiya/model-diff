package ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 金額の正規化・整形（double は使わない）。 */
public final class Money {
    private Money() {
    }

    /** 小数 2 桁にそろえる（入力は 2 桁以内なので丸めは発生しない）。 */
    public static BigDecimal normalize(BigDecimal v) {
        return v.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** 3 桁区切りなし・指数表記なしの小数 2 桁文字列。 */
    public static String format(BigDecimal v) {
        return normalize(v).toPlainString();
    }
}
