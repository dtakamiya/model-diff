package ledger.domain;

/**
 * 収入・支出の区分。CSV と表示では小文字の名前を使う。
 */
public enum EntryType {
    INCOME,
    EXPENSE;

    /** CSV・表示で使う名前（{@code income} / {@code expense}） */
    public String word() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
