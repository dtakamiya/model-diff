package ledger;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 家計簿の 1 件分。amount は小数 2 桁の BigDecimal。 */
public record Entry(long id, LocalDate date, EntryType type, String category,
                    BigDecimal amount, String memo) {
}
