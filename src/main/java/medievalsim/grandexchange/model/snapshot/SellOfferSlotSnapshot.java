package medievalsim.grandexchange.model.snapshot;

import medievalsim.grandexchange.domain.GEOffer;

/**
 * Immutable view of a sell-offer slot.
 */
public record SellOfferSlotSnapshot(
    int slotIndex,
    long offerId,
    String itemStringID,
    int quantityTotal,
    int quantityRemaining,
    int pricePerItem,
    boolean enabled,
    GEOffer.OfferState state,
    int durationHours
) {
    public boolean isOccupied() {
        return offerId > 0 && itemStringID != null && !itemStringID.isBlank();
    }

    public boolean isActive() {
        return enabled && (state == GEOffer.OfferState.ACTIVE || state == GEOffer.OfferState.PARTIAL);
    }

    public boolean isFinished() {
        return state == GEOffer.OfferState.COMPLETED
            || state == GEOffer.OfferState.EXPIRED
            || state == GEOffer.OfferState.CANCELLED;
    }

    public static SellOfferSlotSnapshot empty(int slotIndex) {
        return new SellOfferSlotSnapshot(
            slotIndex,
            0L,
            null,
            0,
            0,
            0,
            false,
            GEOffer.OfferState.DRAFT,
            0
        );
    }
}
