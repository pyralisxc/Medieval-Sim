package medievalsim.packets;
import java.awt.Rectangle;
import medievalsim.util.ModLogger;
import medievalsim.zones.AdminZone;
import medievalsim.zones.AdminZonesLevelData;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class PacketExpandZone
extends Packet {
    public int zoneID;
    public boolean isProtectedZone;
    public Rectangle expandArea;

    public PacketExpandZone(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneID = reader.getNextInt();
        this.isProtectedZone = reader.getNextBoolean();
        int x = reader.getNextInt();
        int y = reader.getNextInt();
        int width = reader.getNextInt();
        int height = reader.getNextInt();
        this.expandArea = new Rectangle(x, y, width, height);
    }

    public PacketExpandZone(int zoneID, boolean isProtectedZone, Rectangle expandArea) {
        this.zoneID = zoneID;
        this.isProtectedZone = isProtectedZone;
        this.expandArea = expandArea;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneID);
        writer.putNextBoolean(isProtectedZone);
        writer.putNextInt(expandArea.x);
        writer.putNextInt(expandArea.y);
        writer.putNextInt(expandArea.width);
        writer.putNextInt(expandArea.height);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Validate using ZoneAPI
            medievalsim.util.ZoneAPI.ZoneContext ctx = medievalsim.util.ZoneAPI.forClient(client)
                .withPacketName("PacketExpandZone")
                .requireAnyZone(this.zoneID, !this.isProtectedZone)
                .build();
            if (!ctx.isValid()) return;

            AdminZone zone = ctx.getAdminZone();
            
            // Snapshot old edge tiles for differential barrier removal
            java.util.Map<Integer, java.util.Collection<java.awt.Point>> oldEdgesSnapshot = new java.util.HashMap<>();
            try {
                necesse.engine.util.PointHashSet edge = zone.zoning.getEdgeTiles();
                if (edge != null) {
                    java.util.List<java.awt.Point> l = new java.util.ArrayList<>();
                    for (Object o : edge) if (o instanceof java.awt.Point) l.add(new java.awt.Point((java.awt.Point)o));
                    oldEdgesSnapshot.put(zone.uniqueID, l);
                }
            } catch (Exception e) { /* best-effort snapshot */ }

            // Expand zone
            boolean changed = zone.expand(this.expandArea);
            if (changed) {
                AdminZonesLevelData localZoneData = AdminZonesLevelData.getZoneData(ctx.getLevel(), false);
                if (localZoneData != null) {
                    localZoneData.resolveAfterZoneChange(zone, ctx.getLevel(), server, this.isProtectedZone, oldEdgesSnapshot);
                }
                ModLogger.info("Expanded zone " + this.zoneID + " (" + zone.name + ") by " + client.getName());
            }
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketExpandZone.processServer", e);
        }
    }
}

