package medievalsim.grandexchange.model.snapshot;

import medievalsim.grandexchange.domain.GEOffer;

/**
 * Immutable representation of a single market listing row transmitted from the server.
 */
public record MarketListingSnapshot(
    long offerId,
    String itemStringID,
    int quantityTotal,
    int quantityRemaining,
    int pricePerItem,
    long sellerAuth,
    String sellerName,
    long expirationTime,
    long createdTime,
    GEOffer.OfferState state
) {
    public boolean isActive() {
        return state == GEOffer.OfferState.ACTIVE || state == GEOffer.OfferState.PARTIAL;
    }
}
