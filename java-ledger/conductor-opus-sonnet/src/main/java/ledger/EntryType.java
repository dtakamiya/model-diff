package ledger;

/** 収支の種別。 */
public enum EntryType {
    INCOME("income"),
    EXPENSE("expense");

    private final String label;

    EntryType(String label) {
        this.label = label;
    }

    /** ファイル・出力に使う小文字表記。 */
    public String label() {
        return label;
    }

    /** 小文字の income / expense のみ受け付ける。それ以外は null。 */
    public static EntryType fromLabel(String s) {
        for (EntryType t : values()) {
            if (t.label.equals(s)) {
                return t;
            }
        }
        return null;
    }
}
