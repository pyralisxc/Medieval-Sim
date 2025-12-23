package medievalsim.grandexchange.model.snapshot;

/**
 * Server-authoritative configuration for the Defaults tab.
 */
public record DefaultsConfigSnapshot(
    long ownerAuth,
    int sellSlotMin,
    int sellSlotMax,
    int sellSlotConfigured,
    int buySlotMin,
    int buySlotMax,
    int buySlotConfigured,
    boolean autoClearEnabled,
    int stagingSlotIndex
) {
    public static DefaultsConfigSnapshot empty(long ownerAuth) {
        return new DefaultsConfigSnapshot(
            ownerAuth,
            0,
            0,
            0,
            0,
            0,
            0,
            false,
            0
        );
    }
}
