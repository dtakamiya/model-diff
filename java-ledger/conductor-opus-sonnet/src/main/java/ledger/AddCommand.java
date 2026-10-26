package ledger;

import java.math.BigDecimal;
import java.time.LocalDate;

/** add: 1 件追加して採番した ID を出力する。 */
public final class AddCommand implements Command {
    private final LocalDate date;
    private final EntryType type;
    private final String category;
    private final BigDecimal amount;
    private final String memo;

    public AddCommand(LocalDate date, EntryType type, String category, BigDecimal amount, String memo) {
        this.date = date;
        this.type = type;
        this.category = category;
        this.amount = amount;
        this.memo = memo;
    }

    @Override
    public String execute(LedgerRepository repo) {
        LedgerData data = repo.load();
        Entry e = data.add(date, type, category, amount, memo);
        repo.save(data);
        return e.id() + "\n";
    }
}
