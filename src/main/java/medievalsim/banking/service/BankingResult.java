package medievalsim.banking.service;

public final class BankingResult {
    public enum Status {
        SUCCESS,
        INSUFFICIENT_COINS,
        INVALID_AMOUNT,
        INVENTORY_FULL,
        PIN_INVALID,
        PIN_LOCKED,
        ERROR
    }

    private final Status status;
    private final int amountProcessed;
    private final long remaining;
    private final String messageKey;

    private BankingResult(Status status, int amountProcessed, long remaining, String messageKey) {
        this.status = status;
        this.amountProcessed = amountProcessed;
        this.remaining = remaining;
        this.messageKey = messageKey;
    }

    public static BankingResult success(int amountProcessed) {
        return new BankingResult(Status.SUCCESS, amountProcessed, 0, null);
    }

    public static BankingResult partial(int amountProcessed, long remaining, String key) {
        return new BankingResult(Status.SUCCESS, amountProcessed, remaining, key);
    }

    public static BankingResult failure(Status status, String key) {
        return new BankingResult(status, 0, 0, key);
    }

    public Status getStatus() {
        return status;
    }

    public int getAmountProcessed() {
        return amountProcessed;
    }

    public long getRemaining() {
        return remaining;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
