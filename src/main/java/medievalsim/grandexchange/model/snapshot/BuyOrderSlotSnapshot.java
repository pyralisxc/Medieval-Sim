package medievalsim.grandexchange.model.snapshot;

import medievalsim.grandexchange.domain.BuyOrder;

/**
 * Immutable view of a single buy-order slot.
 */
public record BuyOrderSlotSnapshot(
    int slotIndex,
    long orderId,
    String itemStringID,
    int quantityTotal,
    int quantityRemaining,
    int pricePerItem,
    boolean enabled,
    BuyOrder.BuyOrderState state,
    int durationDays,
    Analytics analytics,
    PersonalHistory personalHistory
) {
    public boolean isOccupied() {
        return orderId > 0 && itemStringID != null && !itemStringID.isBlank();
    }

    public record Analytics(
        int guidePrice,
        int vwapPrice,
        int low24h,
        int high24h,
        int tradeVolume,
        int tradeCount,
        long lastTradeTimestamp
    ) {
    }

    public record PersonalHistory(
        int pricePerItem,
        int quantity,
        long timestamp
    ) {
    }
}
