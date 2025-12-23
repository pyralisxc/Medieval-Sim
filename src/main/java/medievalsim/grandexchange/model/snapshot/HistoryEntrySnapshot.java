package medievalsim.grandexchange.model.snapshot;

/**
 * Lightweight DTO describing a single sale or purchase history entry for the
 * Grand Exchange history tab.
 */
public record HistoryEntrySnapshot(
    String itemStringID,
    int quantityTraded,
    int pricePerItem,
    int totalCoins,
    boolean partial,
    String counterpartyName,
    long timestamp,
    boolean isSale
) {
    /**
     * Backward-compatible constructor for entries without isSale flag (defaults to sale).
     * @deprecated Use the full constructor with isSale parameter.
     */
    @Deprecated
    public HistoryEntrySnapshot(String itemStringID, int quantityTraded, int pricePerItem,
                                int totalCoins, boolean partial, String counterpartyName, long timestamp) {
        this(itemStringID, quantityTraded, pricePerItem, totalCoins, partial, counterpartyName, timestamp, true);
    }
}
