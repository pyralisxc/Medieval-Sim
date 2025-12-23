package medievalsim.packets;
import java.awt.Rectangle;
import medievalsim.util.ModLogger;
import medievalsim.zones.domain.AdminZone;
import medievalsim.zones.domain.ZoneType;
import medievalsim.zones.service.BarrierPlacementWorker;
import medievalsim.zones.domain.PvPZone;
import medievalsim.zones.service.ZoneHelper;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class PacketCreateZone
extends Packet {
    public ZoneType zoneType;
    public String zoneName;
    public Rectangle initialArea;

    public PacketCreateZone(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneType = ZoneType.fromId(reader.getNextInt());
        this.zoneName = reader.getNextString();
        int x = reader.getNextInt();
        int y = reader.getNextInt();
        int width = reader.getNextInt();
        int height = reader.getNextInt();
        
        // Input sanitization: prevent integer overflow and unreasonable zone sizes
        if (width < medievalsim.util.Constants.Zone.MIN_ZONE_DIMENSION || 
            width > medievalsim.util.Constants.Zone.MAX_ZONE_WIDTH || 
            height < medievalsim.util.Constants.Zone.MIN_ZONE_DIMENSION || 
            height > medievalsim.util.Constants.Zone.MAX_ZONE_HEIGHT) {
            
            // Report which dimension is invalid
            String fieldName = (width < 1 || width > 10000) ? "width" : "height";
            int actual = (width < 1 || width > 10000) ? width : height;
            
            throw new IllegalArgumentException(
                medievalsim.util.ErrorMessageBuilder.buildOutOfRangeMessage(
                    "Zone " + fieldName,
                    medievalsim.util.Constants.Zone.MIN_ZONE_DIMENSION,
                    Math.max(medievalsim.util.Constants.Zone.MAX_ZONE_WIDTH, medievalsim.util.Constants.Zone.MAX_ZONE_HEIGHT),
                    actual
                )
            );
        }
        
        this.initialArea = new Rectangle(x, y, width, height);
    }

    public PacketCreateZone(ZoneType zoneType, String zoneName, Rectangle initialArea) {
        this.zoneType = zoneType;
        this.zoneName = zoneName;
        this.initialArea = initialArea;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneType.getId());
        writer.putNextString(zoneName);
        writer.putNextInt(initialArea.x);
        writer.putNextInt(initialArea.y);
        writer.putNextInt(initialArea.width);
        writer.putNextInt(initialArea.height);
    }

    /**
     * Legacy constructor for backward compatibility.
     * @deprecated Use {@link #PacketCreateZone(ZoneType, String, Rectangle)} instead
     */
    @Deprecated
    public PacketCreateZone(boolean isProtectedZone, String zoneName, Rectangle initialArea) {
        this(isProtectedZone ? ZoneType.PROTECTED : ZoneType.PVP, zoneName, initialArea);
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
            
            // Create zone based on type
            AdminZone zone;
            switch (this.zoneType) {
                case PVP:
                    zone = ZoneHelper.createPvPZone(ctx.getLevel(), validatedName, client);
                    break;
                case GUILD:
                    zone = ZoneHelper.createGuildZone(ctx.getLevel(), validatedName, client);
                    break;
                default:
                    zone = ZoneHelper.createProtectedZone(ctx.getLevel(), validatedName, client);
                    break;
            }
                
            if (zone == null) {
                String errorMsg = medievalsim.util.ErrorMessageBuilder.buildOperationFailedMessage(
                    "Zone creation", 
                    "Unable to create zone. Please try again."
                );
                client.sendChatMessage(errorMsg);
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
            server.network.sendToAllClients((Packet)new PacketZoneChanged(zone));
            // Defer saving to the central resolver or autosave to avoid large synchronous saves here
            
        } catch (IllegalArgumentException e) {
            // Input validation failure - send user-friendly message
            client.sendChatMessage(e.getMessage());
            ModLogger.warn("Invalid input in PacketCreateZone from %s: %s", client.getName(), e.getMessage());
        } catch (Exception e) {
            String errorMsg = medievalsim.util.ErrorMessageBuilder.buildOperationFailedMessage(
                "Zone creation",
                "An unexpected error occurred. Please contact an administrator."
            );
            client.sendChatMessage(errorMsg);
            ModLogger.error("Exception in PacketCreateZone.processServer", e);
        }
    }
}

