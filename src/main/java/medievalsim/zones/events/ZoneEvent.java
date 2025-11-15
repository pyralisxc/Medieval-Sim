package medievalsim.zones.events;

import medievalsim.zones.AdminZone;
import necesse.engine.network.server.ServerClient;

/**
 * Base class for all zone-related events.
 * 
 * Provides common event infrastructure including timestamps, zone references,
 * and player context for zone operations.
 */
public abstract class ZoneEvent {
    private final long timestamp;
    private final AdminZone zone;
    private final ServerClient client; // May be null for system events
    private final ZoneEventType type;
    
    protected ZoneEvent(ZoneEventType type, AdminZone zone, ServerClient client) {
        this.timestamp = System.currentTimeMillis();
        this.type = type;
        this.zone = zone;
        this.client = client;
    }
    
    protected ZoneEvent(ZoneEventType type, AdminZone zone) {
        this(type, zone, null);
    }
    
    // Getters
    public long getTimestamp() { return timestamp; }
    public AdminZone getZone() { return zone; }
    public ServerClient getClient() { return client; }
    public ZoneEventType getType() { return type; }
    public boolean hasClient() { return client != null; }
    
    // Utility methods
    public String getZoneName() {
        return zone != null ? zone.name : "Unknown";
    }
    
    public String getPlayerName() {
        return client != null ? client.getName() : "System";
    }
    
    /**
     * Get a human-readable description of this event.
     * Subclasses should override to provide specific details.
     */
    public String getDescription() {
        return String.format("%s event for zone '%s' by %s", 
            type.getDisplayName(), getZoneName(), getPlayerName());
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s", type.name(), getDescription());
    }
}