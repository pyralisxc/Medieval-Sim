package medievalsim.grandexchange.ui.viewmodel;

import medievalsim.grandexchange.model.snapshot.HistoryDeltaPayload;
import medievalsim.grandexchange.model.snapshot.HistoryEntrySnapshot;
import medievalsim.grandexchange.model.snapshot.HistoryTabSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-side view state for the History tab.
 * Tracks history entries, filtering, pagination, and unseen entry badges.
 */
public final class HistoryTabState {
    private static final int MAX_HISTORY_ENTRIES = 50;
    private HistoryFilter activeFilter = HistoryFilter.ALL;
    private int pageIndex;
    private final int pageSize = 10;
    private List<HistoryEntrySnapshot> entries = Collections.emptyList();
    private int totalItemsPurchased;
    private int totalItemsSold;
    private int totalSellOffersCreated;
    private int totalSellOffersCompleted;
    private int totalBuyOrdersCreated;
    private int totalBuyOrdersCompleted;
    private long lastViewedTimestamp;
    private long latestEntryTimestamp;
    private int unseenCount;

    public HistoryFilter getActiveFilter() {
        return activeFilter;
    }

    public void setActiveFilter(HistoryFilter filter) {
        if (filter == null) {
            return;
        }
        if (this.activeFilter != filter) {
            this.activeFilter = filter;
            this.pageIndex = 0;
        }
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public int getPageSize() {
        return pageSize;
    }

    public List<HistoryEntrySnapshot> getEntries() {
        return entries;
    }

    public int getTotalItemsPurchased() {
        return totalItemsPurchased;
    }

    public int getTotalItemsSold() {
        return totalItemsSold;
    }

    public int getTotalSellOffersCreated() {
        return totalSellOffersCreated;
    }

    public int getTotalSellOffersCompleted() {
        return totalSellOffersCompleted;
    }

    public int getTotalBuyOrdersCreated() {
        return totalBuyOrdersCreated;
    }

    public int getTotalBuyOrdersCompleted() {
        return totalBuyOrdersCompleted;
    }

    public boolean hasUnseenEntries() {
        return unseenCount > 0;
    }

    public int getUnseenCount() {
        return unseenCount;
    }

    public long getLastViewedTimestamp() {
        return lastViewedTimestamp;
    }

    public long getLatestEntryTimestamp() {
        return latestEntryTimestamp;
    }

    public boolean isEntryUnseen(long timestamp) {
        return timestamp > lastViewedTimestamp;
    }

    public void markEntriesSeen() {
        lastViewedTimestamp = Math.max(lastViewedTimestamp, latestEntryTimestamp);
        unseenCount = 0;
    }

    public void applySnapshot(HistoryTabSnapshot snapshot) {
        if (snapshot == null) {
            this.entries = Collections.emptyList();
            this.totalItemsPurchased = 0;
            this.totalItemsSold = 0;
            this.totalSellOffersCreated = 0;
            this.totalSellOffersCompleted = 0;
            this.totalBuyOrdersCreated = 0;
            this.totalBuyOrdersCompleted = 0;
            this.latestEntryTimestamp = 0L;
            this.unseenCount = 0;
            clampPageIndex(0);
            return;
        }
        this.entries = immutableEntries(snapshot.entries());
        this.totalItemsPurchased = snapshot.totalItemsPurchased();
        this.totalItemsSold = snapshot.totalItemsSold();
        this.totalSellOffersCreated = snapshot.totalSellOffersCreated();
        this.totalSellOffersCompleted = snapshot.totalSellOffersCompleted();
        this.totalBuyOrdersCreated = snapshot.totalBuyOrdersCreated();
        this.totalBuyOrdersCompleted = snapshot.totalBuyOrdersCompleted();
        this.latestEntryTimestamp = entries.stream()
            .mapToLong(HistoryEntrySnapshot::timestamp)
            .max()
            .orElse(0L);
        long serverBaseline = Math.max(0L, snapshot.lastHistoryViewedTimestamp());
        if (serverBaseline > lastViewedTimestamp) {
            lastViewedTimestamp = serverBaseline;
        }
        if (lastViewedTimestamp == 0L && latestEntryTimestamp > 0L) {
            lastViewedTimestamp = latestEntryTimestamp;
            unseenCount = 0;
        } else {
            refreshUnseenCount();
        }
        clampPageIndex(this.entries.size());
    }

    public void applyDelta(HistoryDeltaPayload payload) {
        if (payload == null) {
            return;
        }
        this.totalItemsPurchased = payload.totalItemsPurchased();
        this.totalItemsSold = payload.totalItemsSold();
        this.totalSellOffersCreated = payload.totalSellOffersCreated();
        this.totalSellOffersCompleted = payload.totalSellOffersCompleted();
        this.totalBuyOrdersCreated = payload.totalBuyOrdersCreated();
        this.totalBuyOrdersCompleted = payload.totalBuyOrdersCompleted();
        mergeEntries(payload.newEntries());
        this.latestEntryTimestamp = Math.max(latestEntryTimestamp, payload.latestEntryTimestamp());
        long serverBaseline = Math.max(0L, payload.serverBaselineTimestamp());
        if (serverBaseline > lastViewedTimestamp) {
            lastViewedTimestamp = serverBaseline;
        }
        refreshUnseenCount();
        clampPageIndex(entries.size());
    }

    public void synchronizeBadge(int badgeCount, long latestTimestampFromServer, long serverBaselineTimestamp) {
        this.latestEntryTimestamp = Math.max(latestEntryTimestamp, Math.max(0L, latestTimestampFromServer));
        long normalizedBaseline = Math.max(0L, serverBaselineTimestamp);
        if (normalizedBaseline > lastViewedTimestamp) {
            lastViewedTimestamp = normalizedBaseline;
        }
        this.unseenCount = Math.max(0, badgeCount);
    }

    private void mergeEntries(List<HistoryEntrySnapshot> additions) {
        if (additions == null || additions.isEmpty()) {
            return;
        }
        if (entries.isEmpty()) {
            List<HistoryEntrySnapshot> truncated = additions.size() > MAX_HISTORY_ENTRIES
                ? additions.subList(0, MAX_HISTORY_ENTRIES)
                : additions;
            this.entries = immutableEntries(truncated);
            return;
        }
        Set<Long> knownTimestamps = new HashSet<>();
        for (HistoryEntrySnapshot entry : entries) {
            knownTimestamps.add(entry.timestamp());
        }
        List<HistoryEntrySnapshot> merged = new ArrayList<>(Math.min(MAX_HISTORY_ENTRIES, additions.size() + entries.size()));
        for (HistoryEntrySnapshot entry : additions) {
            if (knownTimestamps.add(entry.timestamp())) {
                merged.add(entry);
            }
        }
        for (HistoryEntrySnapshot entry : entries) {
            if (merged.size() >= MAX_HISTORY_ENTRIES) {
                break;
            }
            merged.add(entry);
        }
        if (merged.size() > MAX_HISTORY_ENTRIES) {
            merged = merged.subList(0, MAX_HISTORY_ENTRIES);
        }
        this.entries = immutableEntries(merged);
    }

    private void refreshUnseenCount() {
        if (entries.isEmpty()) {
            unseenCount = 0;
            return;
        }
        long baseline = lastViewedTimestamp;
        unseenCount = (int) entries.stream()
            .filter(entry -> entry.timestamp() > baseline)
            .count();
    }

    private static List<HistoryEntrySnapshot> immutableEntries(List<HistoryEntrySnapshot> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        int limit = Math.min(MAX_HISTORY_ENTRIES, source.size());
        return Collections.unmodifiableList(new ArrayList<>(source.subList(0, limit)));
    }

    public void clampPageIndex(int totalEntries) {
        int maxPage = Math.max(1, (int) Math.ceil(totalEntries / (double) pageSize));
        if (pageIndex >= maxPage) {
            pageIndex = Math.max(0, maxPage - 1);
        }
    }

    public int getTotalPages(int totalEntries) {
        return Math.max(1, (int) Math.ceil(totalEntries / (double) pageSize));
    }

    public boolean hasPreviousPage() {
        return pageIndex > 0;
    }

    public boolean hasNextPage(int totalEntries) {
        int maxPage = getTotalPages(totalEntries);
        return pageIndex < maxPage - 1;
    }

    public void goToPreviousPage() {
        if (pageIndex > 0) {
            pageIndex--;
        }
    }

    public void goToNextPage(int totalEntries) {
        if (hasNextPage(totalEntries)) {
            pageIndex++;
        }
    }

    public enum HistoryFilter {
        ALL,
        SALES,
        PURCHASES
    }
}
