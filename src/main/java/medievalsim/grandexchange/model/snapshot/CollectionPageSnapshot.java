package medievalsim.grandexchange.model.snapshot;

import java.util.Collections;
import java.util.List;

/**
 * Snapshot of a single collection box page.
 */
public record CollectionPageSnapshot(
    long ownerAuth,
    int pageIndex,
    int totalPages,
    int totalItems,
    int pageSize,
    boolean depositToBankPreferred,
    boolean autoSendToBank,
    boolean notifyPartialSales,
    boolean playSoundOnSale,
    List<CollectionEntrySnapshot> entries
) {
    public CollectionPageSnapshot {
        entries = entries == null ? Collections.emptyList() : List.copyOf(entries);
    }

    public static CollectionPageSnapshot empty(long ownerAuth) {
        return new CollectionPageSnapshot(ownerAuth, 0, 1, 0, 10, false, false, false, false, Collections.emptyList());
    }
}
