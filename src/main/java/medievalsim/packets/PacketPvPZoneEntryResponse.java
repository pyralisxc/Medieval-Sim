package medievalsim.packets;
import medievalsim.util.ModLogger;
import medievalsim.zones.domain.PvPZone;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class PacketPvPZoneEntryResponse
extends Packet {
    public int zoneID;
    public boolean acceptEntry;

    public PacketPvPZoneEntryResponse(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneID = reader.getNextInt();
        this.acceptEntry = reader.getNextBoolean();
    }

    public PacketPvPZoneEntryResponse(int zoneID, boolean acceptEntry) {
        this.zoneID = zoneID;
        this.acceptEntry = acceptEntry;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneID);
        writer.putNextBoolean(acceptEntry);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Use ZoneAPI builder for validation - replaces 30+ lines of boilerplate
            medievalsim.util.ZoneAPI.ZoneContext ctx = medievalsim.util.ZoneAPI.forClient(client)
                .withPacketName("PacketPvPZoneEntryResponse")
                .requirePvPZone(this.zoneID)
                .checkReEntryCooldown(server)
                .build();
            
            if (!ctx.isValid()) return; // All errors handled automatically
            
            if (this.acceptEntry) {
                // Teleport player into zone
                ctx.teleportIntoZone(server);
                
                // Enter zone and enable PvP
                ctx.enterZone();
                ctx.enablePvP(server);
                
                // Grant spawn immunity
                ctx.grantSpawnImmunity();
                
                // Send confirmation message
                PvPZone zone = ctx.getPvPZone();
                String damagePercentStr = medievalsim.zones.domain.PvPZone.formatDamagePercent(zone.damageMultiplier);
                client.sendChatMessage(necesse.engine.localization.Localization.translate("message", "zone.pvp.entereddetails", "name", zone.name, "damage", damagePercentStr, "lock", zone.combatLockSeconds));
                ModLogger.info("Player " + client.getName() + " entered PvP zone " + this.zoneID + " (" + zone.name + ")");
            } else {
                client.sendChatMessage(necesse.engine.localization.Localization.translate("message", "zone.pvp.chosestayoutside"));
            }
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketPvPZoneEntryResponse.processServer", e);
        }
    }
}

