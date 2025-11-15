package medievalsim.zones.events;

import medievalsim.zones.AdminZone;
import necesse.engine.network.server.ServerClient;

/**
 * Event fired when a zone is created.
 */
public class ZoneCreatedEvent extends ZoneEvent {
    
    public ZoneCreatedEvent(AdminZone zone, ServerClient creator) {
        super(ZoneEventType.ZONE_CREATED, zone, creator);
    }
    
    @Override
    public String getDescription() {
        return String.format("Zone '%s' (%s) created by %s", 
            getZoneName(), getZone().getTypeID(), getPlayerName());
    }
}

/**
 * Event fired when a zone is modified (boundaries, settings, etc.).
 */
class ZoneModifiedEvent extends ZoneEvent {
    private final String changeDescription;
    
    public ZoneModifiedEvent(AdminZone zone, ServerClient modifier, String changeDescription) {
        super(ZoneEventType.ZONE_MODIFIED, zone, modifier);
        this.changeDescription = changeDescription;
    }
    
    public String getChangeDescription() {
        return changeDescription;
    }
    
    @Override
    public String getDescription() {
        return String.format("Zone '%s' modified by %s: %s", 
            getZoneName(), getPlayerName(), changeDescription);
    }
}

/**
 * Event fired when a zone is renamed.
 */
class ZoneRenamedEvent extends ZoneEvent {
    private final String oldName;
    private final String newName;
    
    public ZoneRenamedEvent(AdminZone zone, ServerClient renamer, String oldName, String newName) {
        super(ZoneEventType.ZONE_RENAMED, zone, renamer);
        this.oldName = oldName;
        this.newName = newName;
    }
    
    public String getOldName() {
        return oldName;
    }
    
    public String getNewName() {
        return newName;
    }
    
    @Override
    public String getDescription() {
        return String.format("Zone renamed from '%s' to '%s' by %s", 
            oldName, newName, getPlayerName());
    }
}