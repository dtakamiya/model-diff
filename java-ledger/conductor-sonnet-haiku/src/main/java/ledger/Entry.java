package ledger;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 家計簿の 1 件分のレコード。
 *
 * @param id       採番された ID（1 始まり）
 * @param date     日付
 * @param type     収入・支出の別
 * @param category カテゴリ（1 文字以上）
 * @param amount   金額（正の値、小数 2 桁で保存する）
 * @param memo     メモ（空文字可）
 */
public record Entry(
        long id,
        LocalDate date,
        EntryType type,
        String category,
        BigDecimal amount,
        String memo) {
}
