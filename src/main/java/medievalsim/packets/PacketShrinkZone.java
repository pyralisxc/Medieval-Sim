package medievalsim.packets;
import java.awt.Rectangle;
import medievalsim.util.ModLogger;
import medievalsim.zones.domain.AdminZone;
import medievalsim.zones.domain.AdminZonesLevelData;
import medievalsim.zones.domain.GuildZone;
import medievalsim.zones.domain.PvPZone;
import medievalsim.zones.domain.ZoneType;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class PacketShrinkZone
extends Packet {
    public int zoneID;
    public ZoneType zoneType;
    public Rectangle shrinkArea;

    public PacketShrinkZone(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneID = reader.getNextInt();
        this.zoneType = ZoneType.fromId(reader.getNextInt());
        int x = reader.getNextInt();
        int y = reader.getNextInt();
        int width = reader.getNextInt();
        int height = reader.getNextInt();
        this.shrinkArea = new Rectangle(x, y, width, height);
    }

    public PacketShrinkZone(int zoneID, ZoneType zoneType, Rectangle shrinkArea) {
        this.zoneID = zoneID;
        this.zoneType = zoneType;
        this.shrinkArea = shrinkArea;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneID);
        writer.putNextInt(zoneType.getId());
        writer.putNextInt(shrinkArea.x);
        writer.putNextInt(shrinkArea.y);
        writer.putNextInt(shrinkArea.width);
        writer.putNextInt(shrinkArea.height);
    }

    /**
     * Legacy constructor for backward compatibility.
     * @deprecated Use {@link #PacketShrinkZone(int, ZoneType, Rectangle)} instead
     */
    @Deprecated
    public PacketShrinkZone(int zoneID, boolean isProtectedZone, Rectangle shrinkArea) {
        this(zoneID, isProtectedZone ? ZoneType.PROTECTED : ZoneType.PVP, shrinkArea);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Validate using ZoneAPI
            medievalsim.util.ZoneAPI.ZoneContext ctx = medievalsim.util.ZoneAPI.forClient(client)
                .withPacketName("PacketShrinkZone")
                .requireZoneByType(this.zoneID, this.zoneType)
                .build();
            if (!ctx.isValid()) return;

            AdminZone zone = ctx.getAdminZone();
            
            // Snapshot old edge tiles BEFORE applying the shrink for differential barrier management
            java.util.Map<Integer, java.util.Collection<java.awt.Point>> oldEdgesSnapshot = new java.util.HashMap<>();
            try {
                necesse.engine.util.PointHashSet edge = zone.zoning.getEdgeTiles();
                if (edge != null) {
                    java.util.List<java.awt.Point> l = new java.util.ArrayList<>();
                    for (Object o : edge) if (o instanceof java.awt.Point) l.add(new java.awt.Point((java.awt.Point)o));
                    oldEdgesSnapshot.put(zone.uniqueID, l);
                }
            } catch (Exception e) { /* best-effort snapshot */ }

            // Shrink zone
            boolean changed = zone.shrink(this.shrinkArea);
            if (changed) {
                if (zone.isEmpty()) {
                    ModLogger.info("Zone " + this.zoneID + " (" + zone.name + ") is now empty, auto-deleting");
                    if (zone instanceof PvPZone) {
                        ((PvPZone)zone).removeBarriers(ctx.getLevel());
                    }
                    switch (this.zoneType) {
                        case PVP:
                            ctx.getZoneData().removePvPZone(this.zoneID);
                            break;
                        case GUILD:
                            ctx.getZoneData().removeGuildZone(this.zoneID);
                            break;
                        default:
                            ctx.getZoneData().removeProtectedZone(this.zoneID);
                            break;
                    }
                    server.network.sendToAllClients((Packet)new PacketZoneRemoved(this.zoneID, this.zoneType));
                } else {
                    AdminZonesLevelData localZoneData = AdminZonesLevelData.getZoneData(ctx.getLevel(), false);
                    if (localZoneData != null) {
                        localZoneData.resolveAfterZoneChange(zone, ctx.getLevel(), server, this.zoneType, oldEdgesSnapshot);
                    }
                    ModLogger.info("Shrunk zone " + this.zoneID + " (" + zone.name + ") by " + client.getName());
                }
            }
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketShrinkZone.processServer", e);
        }
    }
}

