/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  necesse.engine.commands.PermissionLevel
 *  necesse.engine.network.NetworkPacket
 *  necesse.engine.network.Packet
 *  necesse.engine.network.PacketReader
 *  necesse.engine.network.PacketWriter
 *  necesse.engine.network.server.Server
 *  necesse.engine.network.server.ServerClient
 *  necesse.level.maps.Level
 */
package medievalsim.packets;

import java.awt.Rectangle;
import medievalsim.util.ModLogger;
import medievalsim.util.ZonePacketValidator;
import medievalsim.zones.AdminZone;
import medievalsim.zones.AdminZonesLevelData;
import medievalsim.zones.PvPZone;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class PacketShrinkZone
extends Packet {
    public int zoneID;
    public boolean isProtectedZone;
    public Rectangle shrinkArea;

    public PacketShrinkZone(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneID = reader.getNextInt();
        this.isProtectedZone = reader.getNextBoolean();
        int x = reader.getNextInt();
        int y = reader.getNextInt();
        int width = reader.getNextInt();
        int height = reader.getNextInt();
        this.shrinkArea = new Rectangle(x, y, width, height);
    }

    public PacketShrinkZone(int zoneID, boolean isProtectedZone, Rectangle shrinkArea) {
        this.zoneID = zoneID;
        this.isProtectedZone = isProtectedZone;
        this.shrinkArea = shrinkArea;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneID);
        writer.putNextBoolean(isProtectedZone);
        writer.putNextInt(shrinkArea.x);
        writer.putNextInt(shrinkArea.y);
        writer.putNextInt(shrinkArea.width);
        writer.putNextInt(shrinkArea.height);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Validate packet (permission, level, zone data)
            ZonePacketValidator.ValidationResult validation =
                ZonePacketValidator.validateZonePacket(server, client, "PacketShrinkZone");
            if (!validation.isValid) return;

            // Zone lookup
            AdminZone zone = this.isProtectedZone ? validation.zoneData.getProtectedZone(this.zoneID) : validation.zoneData.getPvPZone(this.zoneID);
            if (zone == null) {
                ModLogger.warn("Attempted to shrink non-existent zone ID " + this.zoneID);
                return;
            }
            
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
                    if (!this.isProtectedZone && zone instanceof PvPZone) {
                        ((PvPZone)zone).removeBarriers(validation.level);
                    }
                    if (this.isProtectedZone) {
                        validation.zoneData.removeProtectedZone(this.zoneID);
                    } else {
                        validation.zoneData.removePvPZone(this.zoneID);
                    }
                    server.network.sendToAllClients((Packet)new PacketZoneRemoved(this.zoneID, this.isProtectedZone));
                } else {
                    AdminZonesLevelData localZoneData = AdminZonesLevelData.getZoneData(validation.level, false);
                    if (localZoneData != null) {
                        localZoneData.resolveAfterZoneChange(zone, validation.level, server, this.isProtectedZone, oldEdgesSnapshot);
                    }
                    ModLogger.info("Shrunk zone " + this.zoneID + " (" + zone.name + ") by " + client.getName());
                }
            }
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketShrinkZone.processServer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

