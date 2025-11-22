package medievalsim.packets;
import medievalsim.util.ModLogger;
import medievalsim.zones.AdminZone;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class PacketRenameZone
extends Packet {
    public final int zoneUniqueID;
    public final boolean isProtectedZone;
    public final GameMessage newName;

    public PacketRenameZone(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneUniqueID = reader.getNextInt();
        this.isProtectedZone = reader.getNextBoolean();
        this.newName = GameMessage.fromPacket((PacketReader)reader);
    }

    public PacketRenameZone(int zoneUniqueID, boolean isProtectedZone, GameMessage newName) {
        this.zoneUniqueID = zoneUniqueID;
        this.isProtectedZone = isProtectedZone;
        this.newName = newName;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneUniqueID);
        writer.putNextBoolean(isProtectedZone);
        newName.writePacket(writer);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Validate using ZoneAPI
            medievalsim.util.ZoneAPI.ZoneContext ctx = medievalsim.util.ZoneAPI.forClient(client)
                .withPacketName("PacketRenameZone")
                .requireAnyZone(this.zoneUniqueID, !this.isProtectedZone)
                .build();
            if (!ctx.isValid()) return;

            AdminZone zone = ctx.getAdminZone();
            
            // Validate and apply new name
            String translatedName = medievalsim.util.ZoneAPI.validateZoneName(this.newName.translate());
            zone.name = translatedName;

            ModLogger.info("Renamed zone " + this.zoneUniqueID + " to '" + translatedName + "' by " + client.getName());
            PacketZoneChanged changedPacket = new PacketZoneChanged(zone, this.isProtectedZone);
            ctx.getLevel().getServer().network.sendToAllClients((Packet)changedPacket);
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketRenameZone.processServer", e);
        }
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
    }
}

