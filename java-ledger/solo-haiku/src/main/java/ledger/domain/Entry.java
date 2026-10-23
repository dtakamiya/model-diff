package ledger.domain;

import java.time.LocalDate;

/**
 * 家計簿の 1 件分の記録。金額は銭（1/100 円）単位の整数で持つ。
 *
 * @param id          採番された ID（1 始まり）
 * @param date        日付
 * @param type        収入か支出か
 * @param category    カテゴリ
 * @param amountCents 金額（銭単位）
 * @param memo        メモ（無しの場合は空文字）
 */
public record Entry(
        long id,
        LocalDate date,
        EntryType type,
        String category,
        long amountCents,
        String memo) {
}
