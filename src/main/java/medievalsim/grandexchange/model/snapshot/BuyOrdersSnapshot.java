package medievalsim.grandexchange.model.snapshot;

import java.util.Collections;
import java.util.List;

/**
 * Container snapshot of all buy-order slots for a player plus cooldown metadata.
 */
public record BuyOrdersSnapshot(long ownerAuth,
                                List<BuyOrderSlotSnapshot> slots,
                                float creationCooldownSeconds,
                                float toggleCooldownSeconds) {
    public BuyOrdersSnapshot {
        slots = slots == null ? Collections.emptyList() : List.copyOf(slots);
        creationCooldownSeconds = Math.max(0f, creationCooldownSeconds);
        toggleCooldownSeconds = Math.max(0f, toggleCooldownSeconds);
    }

    public static BuyOrdersSnapshot empty(long ownerAuth) {
        return new BuyOrdersSnapshot(ownerAuth, Collections.emptyList(), 0f, 0f);
    }

    public BuyOrderSlotSnapshot slot(int index) {
        if (index < 0 || index >= slots.size()) {
            return null;
        }
        return slots.get(index);
    }
}
