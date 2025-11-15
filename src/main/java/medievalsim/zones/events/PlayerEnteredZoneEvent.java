package medievalsim.zones.events;

import medievalsim.zones.AdminZone;
import necesse.engine.network.server.ServerClient;

/**
 * Event fired when a player enters a zone.
 */
public class PlayerEnteredZoneEvent extends ZoneEvent {
    private final int tileX, tileY;
    
    public PlayerEnteredZoneEvent(AdminZone zone, ServerClient client, int tileX, int tileY) {
        super(ZoneEventType.PLAYER_ENTERED, zone, client);
        this.tileX = tileX;
        this.tileY = tileY;
    }
    
    public int getTileX() { return tileX; }
    public int getTileY() { return tileY; }
    
    @Override
    public String getDescription() {
        return String.format("%s entered zone '%s' at (%d, %d)", 
            getPlayerName(), getZoneName(), tileX, tileY);
    }
}

/**
 * Event fired when a player exits a zone.
 */
class PlayerExitedZoneEvent extends ZoneEvent {
    private final int tileX, tileY;
    
    public PlayerExitedZoneEvent(AdminZone zone, ServerClient client, int tileX, int tileY) {
        super(ZoneEventType.PLAYER_EXITED, zone, client);
        this.tileX = tileX;
        this.tileY = tileY;
    }
    
    public int getTileX() { return tileX; }
    public int getTileY() { return tileY; }
    
    @Override
    public String getDescription() {
        return String.format("%s exited zone '%s' at (%d, %d)", 
            getPlayerName(), getZoneName(), tileX, tileY);
    }
}

/**
 * Event fired when a player is teleported to a zone.
 */
class PlayerTeleportedEvent extends ZoneEvent {
    private final int fromTileX, fromTileY;
    private final int toTileX, toTileY;
    
    public PlayerTeleportedEvent(AdminZone zone, ServerClient client, 
                                int fromTileX, int fromTileY, 
                                int toTileX, int toTileY) {
        super(ZoneEventType.PLAYER_TELEPORTED, zone, client);
        this.fromTileX = fromTileX;
        this.fromTileY = fromTileY;
        this.toTileX = toTileX;
        this.toTileY = toTileY;
    }
    
    public int getFromTileX() { return fromTileX; }
    public int getFromTileY() { return fromTileY; }
    public int getToTileX() { return toTileX; }
    public int getToTileY() { return toTileY; }
    
    @Override
    public String getDescription() {
        return String.format("%s teleported to zone '%s' from (%d, %d) to (%d, %d)", 
            getPlayerName(), getZoneName(), fromTileX, fromTileY, toTileX, toTileY);
    }
}