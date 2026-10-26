package ledger;

/** 収支の種別。 */
public enum EntryType {
    INCOME("income"),
    EXPENSE("expense");

    private final String label;

    EntryType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 小文字の income / expense のみ受け付ける。該当なしは null。 */
    public static EntryType fromLabel(String text) {
        for (EntryType type : values()) {
            if (type.label.equals(text)) {
                return type;
            }
        }
        return null;
    }
}
