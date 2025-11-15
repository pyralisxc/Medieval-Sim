package medievalsim.zones.events.listeners;

import medievalsim.zones.events.ZoneEvent;
import medievalsim.zones.events.ZoneEventListener;
import medievalsim.zones.events.ZoneEventType;
import medievalsim.util.ModLogger;

/**
 * Example listener that logs all zone events for debugging and administration.
 * 
 * This demonstrates how to create event listeners for the zone system.
 * Admin tools can use this for activity tracking and auditing.
 */
public class ZoneLoggingListener implements ZoneEventListener {
    
    private final boolean logAllEvents;
    private final boolean includePlayerEvents;
    
    /**
     * Create a zone logging listener.
     * 
     * @param logAllEvents Whether to log all events or just management events
     * @param includePlayerEvents Whether to include player interaction events
     */
    public ZoneLoggingListener(boolean logAllEvents, boolean includePlayerEvents) {
        this.logAllEvents = logAllEvents;
        this.includePlayerEvents = includePlayerEvents;
    }
    
    /**
     * Create a default logging listener that logs management events only.
     */
    public ZoneLoggingListener() {
        this(false, false);
    }
    
    @Override
    public void onZoneEvent(ZoneEvent event) {
        // Filter events based on configuration
        if (!shouldLogEvent(event)) {
            return;
        }
        
        // Choose log level based on event importance
        switch (event.getType()) {
            case ZONE_CREATED:
            case ZONE_DELETED:
                ModLogger.info("[Zone Activity] %s", event.getDescription());
                break;
                
            case ZONE_MODIFIED:
            case ZONE_RENAMED:
            case PERMISSIONS_CHANGED:
                ModLogger.info("[Zone Change] %s", event.getDescription());
                break;
                
            case PLAYER_ENTERED:
            case PLAYER_EXITED:
                ModLogger.debug("[Zone Traffic] %s", event.getDescription());
                break;
                
            case PVP_COMBAT_STARTED:
            case PVP_COMBAT_ENDED:
                ModLogger.info("[PvP Activity] %s", event.getDescription());
                break;
                
            case ZONE_ERROR:
                ModLogger.warn("[Zone Error] %s", event.getDescription());
                break;
                
            default:
                ModLogger.debug("[Zone Event] %s", event.getDescription());
                break;
        }
    }
    
    /**
     * Determine if this event should be logged based on configuration.
     */
    private boolean shouldLogEvent(ZoneEvent event) {
        if (logAllEvents) {
            return true;
        }
        
        ZoneEventType type = event.getType();
        
        // Always log management and error events
        if (type.isManagementEvent() || type == ZoneEventType.ZONE_ERROR) {
            return true;
        }
        
        // Log player events if enabled
        if (includePlayerEvents && type.isPlayerEvent()) {
            return true;
        }
        
        // Log PvP events
        if (type.isPvPEvent()) {
            return true;
        }
        
        // Log permission changes
        if (type == ZoneEventType.PERMISSIONS_CHANGED || 
            type == ZoneEventType.MEMBER_ADDED || 
            type == ZoneEventType.MEMBER_REMOVED) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get a description of what this listener logs.
     */
    public String getDescription() {
        if (logAllEvents) {
            return "Zone Logging (All Events)";
        }
        
        String description = "Zone Logging (Management";
        if (includePlayerEvents) {
            description += " + Player";
        }
        description += " + PvP + Permissions)";
        
        return description;
    }
}