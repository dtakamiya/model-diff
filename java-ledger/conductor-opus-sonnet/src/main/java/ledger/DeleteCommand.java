package ledger;

/** delete: 指定 ID を削除する。無ければ終了コード 3。 */
public final class DeleteCommand implements Command {
    private final long id;

    public DeleteCommand(long id) {
        this.id = id;
    }

    @Override
    public String execute(LedgerRepository repo) {
        LedgerData data = repo.load();
        if (!data.remove(id)) {
            throw LedgerException.notFound("id not found: " + id);
        }
        repo.save(data);
        return "deleted " + id + "\n";
    }
}
