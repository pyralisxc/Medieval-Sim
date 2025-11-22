package medievalsim.packets;

import medievalsim.util.ModLogger;
import medievalsim.zones.AdminZone;
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
            // Validate using ZoneAPI
            medievalsim.util.ZoneAPI.ZoneContext ctx = medievalsim.util.ZoneAPI.forClient(client)
                .withPacketName("PacketDeleteZone")
                .requireAnyZone(this.zoneID, !this.isProtectedZone)
                .build();
            if (!ctx.isValid()) return;

            AdminZone zone = ctx.getAdminZone();

            // Remove barriers for PvP zones
            if (!this.isProtectedZone && zone instanceof PvPZone) {
                ((PvPZone)zone).removeBarriers(ctx.getLevel());
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
                    playerClient.sendChatMessage(necesse.engine.localization.Localization.translate("message", "zone.pvp.deleted"));
                }
            }

            // Delete zone
            if (this.isProtectedZone) {
                ZoneManager.deleteProtectedZone(ctx.getLevel(), this.zoneID, client);
            } else {
                ZoneManager.deletePvPZone(ctx.getLevel(), this.zoneID, client);
            }
            
            ModLogger.info("Deleted zone " + this.zoneID + " (" + zone.name + ") by " + client.getName());
            server.network.sendToAllClients((Packet)new PacketZoneRemoved(this.zoneID, this.isProtectedZone));
            // Defer saving to the resolver/autosave to avoid heavy synchronous compression during packet processing
            
        } catch (Exception e) {
            ModLogger.error("Exception in PacketDeleteZone.processServer", e);
        }
    }
}

