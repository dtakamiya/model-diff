package ledger.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 家計簿の 1 件分の記録。
 *
 * @param id       1 始まりの連番 ID
 * @param date     日付
 * @param type     収入 / 支出
 * @param category カテゴリ（加工しない元の文字列）
 * @param amount   金額（正の値。スケールは常に 2）
 * @param memo     メモ（省略時は空文字）
 */
public record Entry(long id, LocalDate date, EntryType type, String category, BigDecimal amount, String memo) {

    public Entry {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(memo, "memo");
    }
}
