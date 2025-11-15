package medievalsim.zones.events;

import medievalsim.zones.AdminZone;
import necesse.engine.network.server.ServerClient;

/**
 * Event fired when a zone is deleted.
 */
public class ZoneDeletedEvent extends ZoneEvent {

    public ZoneDeletedEvent(AdminZone zone, ServerClient deleter) {
        super(ZoneEventType.ZONE_DELETED, zone, deleter);
    }

    @Override
    public String getDescription() {
        return String.format("Zone '%s' (%s) deleted by %s",
            getZoneName(), getZone().getTypeID(), getPlayerName());
    }
}