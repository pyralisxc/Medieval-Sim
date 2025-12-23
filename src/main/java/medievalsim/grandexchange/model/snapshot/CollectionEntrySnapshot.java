package medievalsim.grandexchange.model.snapshot;

/**
 * Immutable entry used for rendering the collection box page.
 */
public record CollectionEntrySnapshot(
    int globalIndex,
    String itemStringID,
    int quantity,
    long timestamp,
    String source
) {
}
