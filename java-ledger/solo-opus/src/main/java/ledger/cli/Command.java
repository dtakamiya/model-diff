package ledger.cli;

import ledger.error.LedgerException;
import ledger.model.EntryType;
import ledger.service.LedgerService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/**
 * 検証済みの引数を持つサブコマンド。実行すると標準出力に出す文字列（改行込み）を返す。
 * 出力はコマンドが成功したときだけ書き出すため、ここでは直接出力しない。
 */
sealed interface Command {

    String execute(LedgerService service) throws LedgerException;

    /** {@code add}：1 件登録し、採番した ID を出力する。 */
    record Add(LocalDate date, EntryType type, String category, BigDecimal amount, String memo) implements Command {
        @Override
        public String execute(LedgerService service) throws LedgerException {
            return service.add(date, type, category, amount, memo) + "\n";
        }
    }

    /** {@code list}：一覧を TSV で出力する。 */
    record ListEntries(Optional<YearMonth> month) implements Command {
        @Override
        public String execute(LedgerService service) throws LedgerException {
            return OutputFormatter.formatList(service.list(month));
        }
    }

    /** {@code delete}：指定 ID を削除する。 */
    record Delete(long id) implements Command {
        @Override
        public String execute(LedgerService service) throws LedgerException {
            service.delete(id);
            return "deleted " + id + "\n";
        }
    }

    /** {@code summary}：月次集計を出力する。 */
    record Summary(YearMonth month) implements Command {
        @Override
        public String execute(LedgerService service) throws LedgerException {
            return OutputFormatter.formatSummary(service.summarize(month));
        }
    }
}
