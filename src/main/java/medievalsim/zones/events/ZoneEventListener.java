package medievalsim.zones.events;

/**
 * Interface for zone event listeners.
 * 
 * Implement this interface to receive notifications about zone events.
 * Use the ZoneEventBus to register and unregister listeners.
 */
@FunctionalInterface
public interface ZoneEventListener {
    /**
     * Called when a zone event occurs.
     * 
     * @param event The zone event that occurred
     */
    void onZoneEvent(ZoneEvent event);
}

/**
 * Convenience interface for typed event listeners.
 * Allows filtering events by type at the listener level.
 */
@FunctionalInterface
interface TypedZoneEventListener extends ZoneEventListener {
    /**
     * Get the event types this listener is interested in.
     * If null or empty, all events will be delivered.
     */
    default ZoneEventType[] getInterestedEventTypes() {
        return null; // Listen to all events by default
    }
    
    @Override
    default void onZoneEvent(ZoneEvent event) {
        ZoneEventType[] interestedTypes = getInterestedEventTypes();
        if (interestedTypes == null || interestedTypes.length == 0) {
            // Listen to all events
            onTypedZoneEvent(event);
        } else {
            // Filter by interested types
            for (ZoneEventType type : interestedTypes) {
                if (event.getType() == type) {
                    onTypedZoneEvent(event);
                    break;
                }
            }
        }
    }
    
    /**
     * Called when a zone event of interest occurs.
     */
    void onTypedZoneEvent(ZoneEvent event);
}