package medievalsim.grandexchange.model.event;

/**
 * Client-facing event detailing an instantaneous sale fill on a sell offer.
 * This keeps the UI responsive without waiting for a full inventory snapshot.
 */
public record SellOfferSaleEvent(
    int slotIndex,
    String itemStringID,
    int quantitySold,
    int quantityRemaining,
    int pricePerItem,
    long timestamp
) {
    public SellOfferSaleEvent {
        itemStringID = itemStringID == null ? "" : itemStringID;
        timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
    }

    public boolean isOfferCompleted() {
        return quantityRemaining <= 0;
    }

    public int totalCoins() {
        long total = (long)Math.max(0, quantitySold) * (long)Math.max(0, pricePerItem);
        return (int)Math.min(Integer.MAX_VALUE, total);
    }
}
