package medievalsim.grandexchange.model.snapshot;

import java.util.Collections;
import java.util.List;

/**
 * Delta payload describing incremental changes to the history tab. Used for
 * streaming badge updates without re-sending the entire snapshot.
 */
public record HistoryDeltaPayload(
    long ownerAuth,
    List<HistoryEntrySnapshot> newEntries,
    int totalItemsPurchased,
    int totalItemsSold,
    int totalSellOffersCreated,
    int totalSellOffersCompleted,
    int totalBuyOrdersCreated,
    int totalBuyOrdersCompleted,
    long latestEntryTimestamp,
    long serverBaselineTimestamp
) {
    public HistoryDeltaPayload {
        newEntries = newEntries == null ? Collections.emptyList() : List.copyOf(newEntries);
    }

    public boolean hasEntries() {
        return !newEntries.isEmpty();
    }
}
