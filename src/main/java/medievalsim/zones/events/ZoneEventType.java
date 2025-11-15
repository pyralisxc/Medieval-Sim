package medievalsim.zones.events;

/**
 * Enumeration of all possible zone event types.
 * 
 * These events represent the complete lifecycle of zone operations
 * and player interactions within the zone system.
 */
public enum ZoneEventType {
    // Zone Management Events
    ZONE_CREATED("Zone Created"),
    ZONE_MODIFIED("Zone Modified"),
    ZONE_DELETED("Zone Deleted"),
    ZONE_RENAMED("Zone Renamed"),
    
    // Permission Events
    PERMISSIONS_CHANGED("Permissions Changed"),
    MEMBER_ADDED("Member Added"),
    MEMBER_REMOVED("Member Removed"),
    
    // Player Interaction Events
    PLAYER_ENTERED("Player Entered"),
    PLAYER_EXITED("Player Exited"),
    PLAYER_TELEPORTED("Player Teleported"),
    
    // PvP Zone Specific Events
    PVP_COMBAT_STARTED("PvP Combat Started"),
    PVP_COMBAT_ENDED("PvP Combat Ended"),
    PVP_DAMAGE_DEALT("PvP Damage Dealt"),
    
    // System Events
    ZONE_SYNC("Zone Synchronized"),
    ZONE_ERROR("Zone Error");
    
    private final String displayName;
    
    ZoneEventType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Check if this is a zone management event (creation, modification, deletion)
     */
    public boolean isManagementEvent() {
        return this == ZONE_CREATED || this == ZONE_MODIFIED || 
               this == ZONE_DELETED || this == ZONE_RENAMED;
    }
    
    /**
     * Check if this is a player interaction event
     */
    public boolean isPlayerEvent() {
        return this == PLAYER_ENTERED || this == PLAYER_EXITED || 
               this == PLAYER_TELEPORTED;
    }
    
    /**
     * Check if this is a PvP-related event
     */
    public boolean isPvPEvent() {
        return this == PVP_COMBAT_STARTED || this == PVP_COMBAT_ENDED || 
               this == PVP_DAMAGE_DEALT;
    }
    
    /**
     * Check if this event should trigger UI updates
     */
    public boolean shouldTriggerUIUpdate() {
        return isManagementEvent() || this == PERMISSIONS_CHANGED || 
               this == MEMBER_ADDED || this == MEMBER_REMOVED;
    }
}