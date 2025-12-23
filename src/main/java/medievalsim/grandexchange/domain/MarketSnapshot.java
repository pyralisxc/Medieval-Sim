package medievalsim.grandexchange.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of the market browser at a specific filter/sort/page.
 * Server builds the snapshot and sends it to clients via PacketGESync so the
 * UI never has to query server-side state directly.
 */
public final class MarketSnapshot {

    private final int page;
    private final int totalPages;
    private final int totalResults;
    private final int pageSize;
    private final String filter;
    private final String category;
    private final int sortMode;
    private final List<Entry> entries;
    private final MarketInsightsSummary insightsSummary;

    public MarketSnapshot(int page, int totalPages, int totalResults, int pageSize,
                          String filter, String category, int sortMode,
                          List<Entry> entries, MarketInsightsSummary insightsSummary) {
        this.page = page;
        this.totalPages = totalPages;
        this.totalResults = totalResults;
        this.pageSize = pageSize;
        this.filter = filter == null ? "" : filter;
        this.category = category == null ? "all" : category;
        this.sortMode = sortMode;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.insightsSummary = insightsSummary;
    }

    public static MarketSnapshot empty(int pageSize, String filter, String category, int sortMode) {
        return new MarketSnapshot(0, 1, 0, pageSize, filter, category, sortMode, List.of(), null);
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getTotalResults() {
        return totalResults;
    }

    public int getPageSize() {
        return pageSize;
    }

    public String getFilter() {
        return filter;
    }

    public String getCategory() {
        return category;
    }

    public int getSortMode() {
        return sortMode;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public MarketInsightsSummary getInsightsSummary() {
        return insightsSummary;
    }

    /**
     * Represents a single listing row inside a snapshot.
     */
    public static final class Entry {
        private final long offerId;
        private final String itemStringID;
        private final int quantityTotal;
        private final int quantityRemaining;
        private final int pricePerItem;
        private final long sellerAuth;
        private final String sellerName;
        private final long expirationTime;
        private final long createdTime;
        private final GEOffer.OfferState state;

        public Entry(long offerId, String itemStringID, int quantityTotal, int quantityRemaining,
                     int pricePerItem, long sellerAuth, String sellerName,
                     long expirationTime, long createdTime, GEOffer.OfferState state) {
            this.offerId = offerId;
            this.itemStringID = itemStringID;
            this.quantityTotal = quantityTotal;
            this.quantityRemaining = quantityRemaining;
            this.pricePerItem = pricePerItem;
            this.sellerAuth = sellerAuth;
            this.sellerName = sellerName;
            this.expirationTime = expirationTime;
            this.createdTime = createdTime;
            this.state = state;
        }

        public long getOfferId() {
            return offerId;
        }

        public String getItemStringID() {
            return itemStringID;
        }

        public int getQuantityTotal() {
            return quantityTotal;
        }

        public int getQuantityRemaining() {
            return quantityRemaining;
        }

        public int getPricePerItem() {
            return pricePerItem;
        }

        public long getSellerAuth() {
            return sellerAuth;
        }

        public String getSellerName() {
            return sellerName;
        }

        public long getExpirationTime() {
            return expirationTime;
        }

        public long getCreatedTime() {
            return createdTime;
        }

        public GEOffer.OfferState getState() {
            return state;
        }
    }
}
