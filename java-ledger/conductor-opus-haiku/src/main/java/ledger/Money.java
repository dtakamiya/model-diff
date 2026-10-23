package ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 金額の表示用ユーティリティ。常に小数 2 桁、3 桁区切りなしで出力する。 */
final class Money {

    private Money() {}

    /** 例: 1200 → "1200.00"、1200.5 → "1200.50"、-500 → "-500.00"。 */
    static String format(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
