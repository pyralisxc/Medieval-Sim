package medievalsim.packets;
import java.awt.Rectangle;
import medievalsim.util.ModLogger;
import medievalsim.zones.domain.AdminZone;
import medievalsim.zones.service.BarrierPlacementWorker;
import medievalsim.zones.domain.PvPZone;
import medievalsim.zones.service.ZoneManager;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class PacketCreateZone
extends Packet {
    public boolean isProtectedZone;
    public String zoneName;
    public Rectangle initialArea;

    public PacketCreateZone(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.isProtectedZone = reader.getNextBoolean();
        this.zoneName = reader.getNextString();
        int x = reader.getNextInt();
        int y = reader.getNextInt();
        int width = reader.getNextInt();
        int height = reader.getNextInt();
        this.initialArea = new Rectangle(x, y, width, height);
    }

    public PacketCreateZone(boolean isProtectedZone, String zoneName, Rectangle initialArea) {
        this.isProtectedZone = isProtectedZone;
        this.zoneName = zoneName;
        this.initialArea = initialArea;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextBoolean(isProtectedZone);
        writer.putNextString(zoneName);
        writer.putNextInt(initialArea.x);
        writer.putNextInt(initialArea.y);
        writer.putNextInt(initialArea.width);
        writer.putNextInt(initialArea.height);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Validate using ZoneAPI
            medievalsim.util.ZoneAPI.ZoneContext ctx = medievalsim.util.ZoneAPI.forClient(client)
                .withPacketName("PacketCreateZone")
                .validateClient()
                .build();
            if (!ctx.isValid()) return;

            // Name validation
            String validatedName = medievalsim.util.ZoneAPI.validateZoneName(this.zoneName);
            
            // Create zone
            AdminZone zone = this.isProtectedZone
                ? ZoneManager.createProtectedZone(ctx.getLevel(), validatedName, client)
                : ZoneManager.createPvPZone(ctx.getLevel(), validatedName, client);
                
            if (zone == null) {
                ModLogger.error("Failed to create zone for client " + client.getName());
                return;
            }
            
            // Expand to initial area
            zone.expand(this.initialArea);

            // Handle zone splitting and barrier placement for PvP zones
            if (ctx.getZoneData() != null) {
                java.util.List<AdminZone> affected = ctx.getZoneData().splitZoneIfDisconnected(zone, ctx.getLevel());
                for (AdminZone az : affected) {
                    if (az instanceof PvPZone) {
                        // Queue full-zone barrier placement by iterating edge tiles and enqueueing per-region
                        necesse.engine.util.PointHashSet edge = az.zoning.getEdgeTiles();
                        if (edge != null) {
                            for (Object o : edge) {
                                if (!(o instanceof java.awt.Point)) continue;
                                java.awt.Point p = (java.awt.Point)o;
                                necesse.level.maps.regionSystem.Region region = ctx.getLevel().regionManager.getRegionByTile(p.x, p.y, false);
                                if (region != null) BarrierPlacementWorker.queueZoneRegionPlacement(ctx.getLevel(), (PvPZone)az, region);
                            }
                        }
                    }
                }
            }
            
            ModLogger.info("Created zone " + zone.uniqueID + " (" + zone.name + ") by " + client.getName());
            server.network.sendToAllClients((Packet)new PacketZoneChanged(zone, this.isProtectedZone));
            // Defer saving to the central resolver or autosave to avoid large synchronous saves here
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketCreateZone.processServer", e);
        }
    }
}

