package ledger;

/** 検証済みのコマンド。実行結果の標準出力文字列を返す（エラー時は例外）。 */
public interface Command {
    String execute(LedgerRepository repo);
}
