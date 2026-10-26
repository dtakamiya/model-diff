package ledger.model;

import java.util.Optional;

/** 収支の種別。CLI・ファイル上の表記は小文字の {@code income} / {@code expense}。 */
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

    /** 表記（大文字小文字を区別）から種別を引く。該当なしは空。 */
    public static Optional<EntryType> fromLabel(String text) {
        for (EntryType type : values()) {
            if (type.label.equals(text)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
