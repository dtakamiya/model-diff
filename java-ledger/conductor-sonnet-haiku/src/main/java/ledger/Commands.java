package ledger;

import java.util.List;

/**
 * 検証済みのコマンドを実行し、標準出力に出す行の一覧を返す。
 *
 * <p>処理の流れは「データ読込 → 処理 → 変更があれば保存」。
 * 読み取り系（list / summary）はデータファイルを書き換えない。
 * 出力は呼び出し側（Main）が成功時にまとめて出すので、途中で失敗しても何も出力されない。
 */
public final class Commands {

    private Commands() {
    }

    /**
     * コマンドを実行する。
     *
     * @return 標準出力に出す各行（改行は含まない）
     * @throws InputException    入力が不正な場合（ここでは検証済みのため通常は起きない）
     * @throws DataFileException データファイルの IO エラー
     * @throws NotFoundException delete で存在しない ID を指定した場合
     */
    public static List<String> execute(CommandParser.Invocation invocation) {
        LedgerRepository repository = new LedgerRepository(invocation.dataPath());
        Command command = invocation.command();

        if (command instanceof Command.Add add) {
            Ledger ledger = repository.load();
            long id = ledger.add(add.date(), add.type(), add.category(), add.amount(), add.memo());
            repository.save(ledger);
            return List.of(String.valueOf(id));
        }

        if (command instanceof Command.ListEntries list) {
            Ledger ledger = repository.load();
            return ReportFormatter.entryList(ledger.listSorted(list.month()));
        }

        if (command instanceof Command.Delete delete) {
            Ledger ledger = repository.load();
            if (!ledger.remove(delete.id())) {
                // 存在しない ID のときは保存せず、データは変更しない
                throw new NotFoundException("ID " + delete.id() + " のデータは存在しません");
            }
            repository.save(ledger);
            return List.of("deleted " + delete.id());
        }

        if (command instanceof Command.ShowSummary summary) {
            Ledger ledger = repository.load();
            return ReportFormatter.summary(Summary.of(summary.month(), ledger.entries()));
        }

        throw new IllegalStateException("未対応のコマンドです: " + command);
    }
}
