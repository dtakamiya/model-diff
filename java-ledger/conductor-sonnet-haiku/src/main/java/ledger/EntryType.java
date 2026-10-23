package ledger;

/**
 * 収支の種別。CSV・画面上の表記は小文字の income / expense。
 */
public enum EntryType {
    INCOME("income"),
    EXPENSE("expense");

    private final String label;

    EntryType(String label) {
        this.label = label;
    }

    /** CSV や出力で使う表記（小文字）を返す */
    public String label() {
        return label;
    }
}
