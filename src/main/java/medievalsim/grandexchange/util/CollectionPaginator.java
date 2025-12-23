package medievalsim.grandexchange.util;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.domain.CollectionItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility that slices the collection box into deterministic pages.
 * Keeps pagination rules centralized so both server logic and packets
 * stay in sync with configurable page sizes.
 */
public final class CollectionPaginator {

    private CollectionPaginator() {
    }

    /**
     * Build a {@link Page} snapshot for the requested page index.
     * Page index is clamped to valid bounds and never negative.
     */
    public static Page paginate(List<CollectionItem> source, int requestedPage) {
        List<CollectionItem> items = source == null ? Collections.emptyList() : source;
        int pageSize = Math.max(1, ModConfig.GrandExchange.getCollectionPageSize());
        int totalItems = items.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
        int pageIndex = clampPageIndex(requestedPage, totalPages);

        int fromIndex = Math.min(pageIndex * pageSize, totalItems);
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        List<Entry> entries = new ArrayList<>(Math.max(0, toIndex - fromIndex));
        for (int i = fromIndex; i < toIndex; i++) {
            entries.add(new Entry(i, items.get(i)));
        }

        return new Page(pageIndex, totalPages, totalItems, pageSize, entries);
    }

    /** Clamp requested page into valid range (0..totalPages-1). */
    public static int clampPageIndex(int requestedPage, int totalPages) {
        if (totalPages <= 0) {
            return 0;
        }
        if (requestedPage < 0) {
            return 0;
        }
        return Math.min(requestedPage, totalPages - 1);
    }

    public static final class Page {
        private final int pageIndex;
        private final int totalPages;
        private final int totalItems;
        private final int pageSize;
        private final List<Entry> entries;

        private Page(int pageIndex, int totalPages, int totalItems, int pageSize, List<Entry> entries) {
            this.pageIndex = pageIndex;
            this.totalPages = totalPages;
            this.totalItems = totalItems;
            this.pageSize = pageSize;
            this.entries = Collections.unmodifiableList(entries);
        }

        public int getPageIndex() {
            return pageIndex;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public int getTotalItems() {
            return totalItems;
        }

        public int getPageSize() {
            return pageSize;
        }

        public List<Entry> getEntries() {
            return entries;
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    public static final class Entry {
        private final int globalIndex;
        private final CollectionItem item;

        private Entry(int globalIndex, CollectionItem item) {
            this.globalIndex = globalIndex;
            this.item = item;
        }

        public int getGlobalIndex() {
            return globalIndex;
        }

        public CollectionItem getItem() {
            return item;
        }
    }
}
