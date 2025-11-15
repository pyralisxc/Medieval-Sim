/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  necesse.engine.commands.PermissionLevel
 *  necesse.engine.network.NetworkPacket
 *  necesse.engine.network.Packet
 *  necesse.engine.network.PacketReader
 *  necesse.engine.network.PacketWriter
 *  necesse.engine.network.packet.PacketPlayerPvP
 *  necesse.engine.network.server.Server
 *  necesse.engine.network.server.ServerClient
 *  necesse.level.maps.Level
 */
package medievalsim.packets;


import medievalsim.util.ModLogger;
import medievalsim.util.ZonePacketValidator;
import medievalsim.zones.AdminZone;
import medievalsim.zones.AdminZonesLevelData;
import medievalsim.zones.PvPZone;
import medievalsim.zones.PvPZoneTracker;
import medievalsim.zones.ZoneManager;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketPlayerPvP;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

public class PacketDeleteZone
extends Packet {
    public int zoneID;
    public boolean isProtectedZone;

    public PacketDeleteZone(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneID = reader.getNextInt();
        this.isProtectedZone = reader.getNextBoolean();
    }

    public PacketDeleteZone(int zoneID, boolean isProtectedZone) {
        this.zoneID = zoneID;
        this.isProtectedZone = isProtectedZone;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneID);
        writer.putNextBoolean(isProtectedZone);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Validate packet (permission, level, zone data)
            ZonePacketValidator.ValidationResult validation =
                ZonePacketValidator.validateZonePacket(server, client, "PacketDeleteZone");
            if (!validation.isValid) return;

            // Zone lookup
            AdminZone zone = this.isProtectedZone ? validation.zoneData.getProtectedZone(this.zoneID) : validation.zoneData.getPvPZone(this.zoneID);
            if (zone == null) {
                ModLogger.warn("Attempted to delete non-existent zone ID " + this.zoneID + " (protected=" + this.isProtectedZone + ")");
                return;
            }

            // Remove barriers for PvP zones
            if (!this.isProtectedZone && zone instanceof PvPZone) {
                ((PvPZone)zone).removeBarriers(validation.level);
            }

            // Handle players currently in PvP zone
            if (!this.isProtectedZone) {
                long serverTime = server.world.worldEntity.getTime();
                for (ServerClient playerClient : server.getClients()) {
                    PvPZoneTracker.PlayerPvPState state = PvPZoneTracker.getPlayerState(playerClient);
                    if (state.currentZoneID != this.zoneID) continue;
                    PvPZoneTracker.exitZone(playerClient, serverTime);
                    if (playerClient.pvpEnabled && !server.world.settings.forcedPvP) {
                        playerClient.pvpEnabled = false;
                        server.network.sendToAllClients((Packet)new PacketPlayerPvP(playerClient.slot, false));
                    }
                    playerClient.sendChatMessage("\u00a7cPVP zone deleted - you have been removed from the zone");
                }
            }

            // Delete zone
            if (this.isProtectedZone) {
                ZoneManager.deleteProtectedZone(validation.level, this.zoneID, client);
            } else {
                ZoneManager.deletePvPZone(validation.level, this.zoneID, client);
            }
            
            ModLogger.info("Deleted zone " + this.zoneID + " (" + zone.name + ") by " + client.getName());
            server.network.sendToAllClients((Packet)new PacketZoneRemoved(this.zoneID, this.isProtectedZone));
            // Defer saving to the resolver/autosave to avoid heavy synchronous compression during packet processing
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketDeleteZone.processServer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

