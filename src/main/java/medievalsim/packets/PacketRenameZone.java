/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  necesse.engine.localization.message.GameMessage
 *  necesse.engine.network.NetworkPacket
 *  necesse.engine.network.Packet
 *  necesse.engine.network.PacketReader
 *  necesse.engine.network.PacketWriter
 *  necesse.engine.network.client.Client
 *  necesse.engine.network.server.Server
 *  necesse.engine.network.server.ServerClient
 *  necesse.level.maps.Level
 */
package medievalsim.packets;

import medievalsim.util.ModLogger;
import medievalsim.util.ZonePacketValidator;
import medievalsim.zones.AdminZone;
import medievalsim.zones.AdminZonesLevelData;
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
            // Validate packet (permission, level, zone data)
            ZonePacketValidator.ValidationResult validation =
                ZonePacketValidator.validateZonePacket(server, client, "PacketRenameZone");
            if (!validation.isValid) return;

            // Zone lookup
            AdminZone zone = this.isProtectedZone
                ? (AdminZone)validation.zoneData.getProtectedZones().get(this.zoneUniqueID)
                : (AdminZone)validation.zoneData.getPvPZones().get(this.zoneUniqueID);
                
            if (zone == null) {
                ModLogger.warn("Attempted to rename non-existent zone ID " + this.zoneUniqueID);
                PacketZoneRemoved removedPacket = new PacketZoneRemoved(this.zoneUniqueID, this.isProtectedZone);
                client.sendPacket((Packet)removedPacket);
                return;
            }
            
            // Validate and apply new name
            String translatedName = ZonePacketValidator.validateZoneName(this.newName.translate());
            zone.name = translatedName;

            ModLogger.info("Renamed zone " + this.zoneUniqueID + " to '" + translatedName + "' by " + client.getName());
            PacketZoneChanged changedPacket = new PacketZoneChanged(zone, this.isProtectedZone);
            validation.level.getServer().network.sendToAllClients((Packet)changedPacket);
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketRenameZone.processServer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
    }
}

