package ledger;

/** 存在しない ID を指定した（終了コード 3）。 */
final class NotFoundException extends RuntimeException {

    NotFoundException(String message) {
        super(message);
    }
}
