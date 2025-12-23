package medievalsim.grandexchange.model.snapshot;

import java.util.Collections;
import java.util.List;

import medievalsim.grandexchange.domain.MarketInsightsSummary;

/**
 * Snapshot of the market tab, including pagination and insights metadata.
 */
public record MarketTabSnapshot(
    long viewerAuth,
    int page,
    int totalPages,
    int totalResults,
    int pageSize,
    String filter,
    String category,
    int sortMode,
    List<MarketListingSnapshot> listings,
    MarketInsightsSummary insightsSummary
) {
    public MarketTabSnapshot {
        listings = listings == null ? Collections.emptyList() : List.copyOf(listings);
        filter = filter == null ? "" : filter;
        category = category == null ? "all" : category;
    }

    public static MarketTabSnapshot empty(long viewerAuth) {
        return new MarketTabSnapshot(
            viewerAuth,
            0,
            1,
            0,
            10,
            "",
            "all",
            1,
            Collections.emptyList(),
            null
        );
    }
}
