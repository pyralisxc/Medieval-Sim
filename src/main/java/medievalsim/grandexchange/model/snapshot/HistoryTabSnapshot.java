package medievalsim.grandexchange.model.snapshot;

import java.util.Collections;
import java.util.List;

/**
 * Snapshot containing sale history entries and aggregate stats for the
 * Grand Exchange history tab.
 */
public record HistoryTabSnapshot(
    long ownerAuth,
    List<HistoryEntrySnapshot> entries,
    int totalItemsPurchased,
    int totalItemsSold,
    int totalSellOffersCreated,
    int totalSellOffersCompleted,
    int totalBuyOrdersCreated,
    int totalBuyOrdersCompleted,
    long lastHistoryViewedTimestamp
) {
    public HistoryTabSnapshot {
        entries = entries == null ? Collections.emptyList() : List.copyOf(entries);
    }

    public static HistoryTabSnapshot empty(long ownerAuth) {
        return new HistoryTabSnapshot(
            ownerAuth,
            Collections.emptyList(),
            0,
            0,
            0,
            0,
            0,
            0,
            0L
        );
    }
}
