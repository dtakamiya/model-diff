package ledger;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 家計簿の 1 件分の記録。amount は小数 2 桁に揃えてある前提。 */
record Entry(long id, LocalDate date, String type, String category, BigDecimal amount, String memo) {
}
