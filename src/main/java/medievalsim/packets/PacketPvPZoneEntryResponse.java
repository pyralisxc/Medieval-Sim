/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  necesse.engine.network.NetworkPacket
 *  necesse.engine.network.Packet
 *  necesse.engine.network.PacketReader
 *  necesse.engine.network.PacketWriter
 *  necesse.engine.network.packet.PacketPlayerMovement
 *  necesse.engine.network.packet.PacketPlayerPvP
 *  necesse.engine.network.server.Server
 *  necesse.engine.network.server.ServerClient
 *  necesse.entity.mobs.Mob
 *  necesse.entity.mobs.buffs.ActiveBuff
 *  necesse.level.maps.Level
 *  necesse.level.maps.regionSystem.RegionPositionGetter
 */
package medievalsim.packets;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;

import java.awt.Point;
import medievalsim.zones.AdminZonesLevelData;
import medievalsim.zones.PvPZone;
import medievalsim.zones.PvPZoneTracker;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketPlayerMovement;
import necesse.engine.network.packet.PacketPlayerPvP;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.RegionPositionGetter;

public class PacketPvPZoneEntryResponse
extends Packet {
    public int zoneID;
    public boolean acceptEntry;

    public PacketPvPZoneEntryResponse(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneID = reader.getNextInt();
        this.acceptEntry = reader.getNextBoolean();
    }

    public PacketPvPZoneEntryResponse(int zoneID, boolean acceptEntry) {
        this.zoneID = zoneID;
        this.acceptEntry = acceptEntry;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneID);
        writer.putNextBoolean(acceptEntry);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Player mob validation
            if (client.playerMob == null) {
                ModLogger.error("PacketPvPZoneEntryResponse received for client with null playerMob: " + client.getName());
                return;
            }
            
            // Level validation
            Level level = client.playerMob.getLevel();
            if (level == null) {
                ModLogger.error("Failed to get level for player " + client.getName() + " in PacketPvPZoneEntryResponse");
                return;
            }
            
            // Zone data validation
            AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
            if (zoneData == null) {
                ModLogger.error("Failed to get zone data for level " + level.getIdentifier() + " in PacketPvPZoneEntryResponse");
                return;
            }
            
            // Zone lookup
            PvPZone zone = zoneData.getPvPZone(this.zoneID);
            if (zone == null) {
                client.sendChatMessage("\u00a7cError: PVP zone no longer exists");
                ModLogger.warn("Player " + client.getName() + " attempted to enter non-existent PvP zone ID " + this.zoneID);
                return;
            }
            
            if (this.acceptEntry) {
                // Check re-entry cooldown
                long serverTime = server.world.worldEntity.getTime();
                if (!PvPZoneTracker.canReEnter(client, serverTime)) {
                    int remainingSeconds = PvPZoneTracker.getRemainingReEntryCooldown(client, serverTime);
                    client.sendChatMessage("\u00a7cYou must wait " + remainingSeconds + "s before re-entering a PVP zone");
                    return;
                }
                
                // Find closest tile in zone and teleport player
                Point entryTile = PvPZoneTracker.findClosestTileInZone(zone, client.playerMob.x, client.playerMob.y);
                if (entryTile != null) {
                    float entryX = entryTile.x * 32 + 16;
                    float entryY = entryTile.y * 32 + 16;
                    client.playerMob.dx = 0.0f;
                    client.playerMob.dy = 0.0f;
                    client.playerMob.setPos(entryX, entryY, true);
                    server.network.sendToClientsWithEntity((Packet)new PacketPlayerMovement(client, true), (RegionPositionGetter)client.playerMob);
                }
                
                // Enter zone and enable PvP
                PvPZoneTracker.enterZone(client, zone);
                if (!client.pvpEnabled && !server.world.settings.forcedPvP) {
                    client.pvpEnabled = true;
                    server.network.sendToAllClients((Packet)new PacketPlayerPvP(client.slot, true));
                }
                
                // Grant spawn immunity
                client.playerMob.addBuff(new ActiveBuff("pvpimmunity", (Mob)client.playerMob, ModConfig.Zones.pvpSpawnImmunitySeconds, null), true);
                
                String damagePercentStr = medievalsim.zones.PvPZone.formatDamagePercent(zone.damageMultiplier);
                client.sendChatMessage("\u00a7aEntered PVP zone: " + zone.name + " \u00a77(Damage: " + damagePercentStr + ", Combat Lock: " + zone.combatLockSeconds + "s)");
                ModLogger.info("Player " + client.getName() + " entered PvP zone " + this.zoneID + " (" + zone.name + ")");
            } else {
                client.sendChatMessage("\u00a77You chose to stay outside the PVP zone");
            }
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketPvPZoneEntryResponse.processServer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

