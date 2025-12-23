package medievalsim.grandexchange.model.snapshot;

import java.util.Collections;
import java.util.List;

/**
 * Snapshot of all sell-offer slots for a player.
 */
public record SellOffersSnapshot(long ownerAuth,
                                 int slotCapacity,
                                 List<SellOfferSlotSnapshot> slots) {
    public SellOffersSnapshot {
        slotCapacity = Math.max(1, slotCapacity);
        slots = slots == null ? Collections.emptyList() : List.copyOf(slots);
    }

    public static SellOffersSnapshot empty(long ownerAuth, int slotCapacity) {
        return new SellOffersSnapshot(ownerAuth, slotCapacity, Collections.emptyList());
    }

    public SellOfferSlotSnapshot slot(int index) {
        if (index < 0 || index >= slots.size()) {
            return SellOfferSlotSnapshot.empty(index);
        }
        return slots.get(index);
    }
}
