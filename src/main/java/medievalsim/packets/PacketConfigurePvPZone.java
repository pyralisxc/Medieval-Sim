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


import medievalsim.util.ModLogger;
import medievalsim.util.ZonePacketValidator;
import medievalsim.zones.AdminZonesLevelData;
import medievalsim.zones.PvPZone;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class PacketConfigurePvPZone
extends Packet {
    public int zoneID;
    public float damageMultiplier;
    public int combatLockSeconds;
    public float dotDamageMultiplier;
    public float dotIntervalMultiplier;

    public PacketConfigurePvPZone(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneID = reader.getNextInt();
        this.damageMultiplier = reader.getNextFloat();
        this.dotDamageMultiplier = reader.getNextFloat();
        this.dotIntervalMultiplier = reader.getNextFloat();
        this.combatLockSeconds = reader.getNextInt();
    }

    public PacketConfigurePvPZone(int zoneID, float damageMultiplier, int combatLockSeconds, float dotDamageMultiplier, float dotIntervalMultiplier) {
        this.zoneID = zoneID;
        this.damageMultiplier = damageMultiplier;
        this.combatLockSeconds = combatLockSeconds;
        this.dotDamageMultiplier = dotDamageMultiplier;
        this.dotIntervalMultiplier = dotIntervalMultiplier;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneID);
        writer.putNextFloat(damageMultiplier);
        writer.putNextFloat(dotDamageMultiplier);
        writer.putNextFloat(dotIntervalMultiplier);
        writer.putNextInt(combatLockSeconds);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Validate packet (permission, level, zone data)
            ZonePacketValidator.ValidationResult validation =
                ZonePacketValidator.validateZonePacket(server, client, "PacketConfigurePvPZone");
            if (!validation.isValid) return;

            // Zone lookup
            PvPZone zone = validation.zoneData.getPvPZone(this.zoneID);
            if (zone == null) {
                ModLogger.warn("Attempted to configure non-existent PvP zone ID " + this.zoneID);
                return;
            }
            
            // Apply validated settings
            zone.damageMultiplier = Math.max(0.001f, Math.min(0.1f, this.damageMultiplier));
            zone.combatLockSeconds = Math.max(0, Math.min(10, this.combatLockSeconds));
            zone.dotDamageMultiplier = Math.max(0.01f, Math.min(2.0f, this.dotDamageMultiplier));
            zone.dotIntervalMultiplier = Math.max(0.25f, Math.min(4.0f, this.dotIntervalMultiplier));
            
            ModLogger.info("Configured PvP zone " + this.zoneID + " (" + zone.name + ") by " + client.getName());
            server.network.sendToAllClients((Packet)new PacketZoneChanged(zone, false));
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketConfigurePvPZone.processServer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

