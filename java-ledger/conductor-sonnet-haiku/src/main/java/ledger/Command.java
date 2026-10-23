package ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/**
 * 引数の検証が済んだコマンド。
 * ここまで来た値はすべて正しい形式になっているので、実行側は再検証しない。
 */
public sealed interface Command {

    /** add：1 件追加する */
    record Add(LocalDate date, EntryType type, String category, BigDecimal amount, String memo)
            implements Command {
    }

    /** list：全件、または指定月だけを一覧する */
    record ListEntries(Optional<YearMonth> month) implements Command {
    }

    /** delete：指定 ID を削除する */
    record Delete(long id) implements Command {
    }

    /** summary：指定月を集計する */
    record ShowSummary(YearMonth month) implements Command {
    }
}
