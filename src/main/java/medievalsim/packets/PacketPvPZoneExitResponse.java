package medievalsim.packets;
import medievalsim.util.ModLogger;
import medievalsim.zones.domain.PvPZone;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class PacketPvPZoneExitResponse
extends Packet {
    public int zoneID;
    public boolean acceptExit;

    public PacketPvPZoneExitResponse(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneID = reader.getNextInt();
        this.acceptExit = reader.getNextBoolean();
    }

    public PacketPvPZoneExitResponse(int zoneID, boolean acceptExit) {
        this.zoneID = zoneID;
        this.acceptExit = acceptExit;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneID);
        writer.putNextBoolean(acceptExit);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Use ZoneAPI builder for validation
            medievalsim.util.ZoneAPI.ZoneContext ctx = medievalsim.util.ZoneAPI.forClient(client)
                .withPacketName("PacketPvPZoneExitResponse")
                .requirePvPZone(this.zoneID)
                .checkCombatLock(server)
                .build();
            
            if (!ctx.isValid()) return;
            
            if (this.acceptExit) {
                long serverTime = server.world.worldEntity.getTime();
                
                // Teleport player out of zone
                ctx.teleportOutOfZone(server);
                
                // Exit zone and disable PvP
                ctx.exitZone(serverTime);
                ctx.disablePvP(server);
                
                // Send confirmation message
                PvPZone zone = ctx.getPvPZone();
                client.sendChatMessage(necesse.engine.localization.Localization.translate("message", "zone.pvp.exited", "name", zone.name));
                ModLogger.info("Player " + client.getName() + " exited PvP zone " + this.zoneID + " (" + zone.name + ")");
            } else {
                client.sendChatMessage(necesse.engine.localization.Localization.translate("message", "zone.pvp.chosestay"));
            }
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketPvPZoneExitResponse.processServer", e);
        }
    }
}

