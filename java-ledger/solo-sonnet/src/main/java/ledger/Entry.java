package ledger;

import java.time.LocalDate;

/**
 * 1 件の収支記録。金額は誤差を避けるため銭（1/100 単位）の整数で持つ。
 */
public record Entry(long id, LocalDate date, EntryType type, String category, long amountCents, String memo) {
}
