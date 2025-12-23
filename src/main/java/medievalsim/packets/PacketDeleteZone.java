package medievalsim.packets;

import medievalsim.util.ModLogger;
import medievalsim.zones.domain.AdminZone;
import medievalsim.zones.domain.GuildZone;
import medievalsim.zones.domain.PvPZone;
import medievalsim.zones.domain.ZoneType;
import medievalsim.zones.service.PvPZoneTracker;
import medievalsim.zones.service.ZoneHelper;
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
    public ZoneType zoneType;

    public PacketDeleteZone(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader((Packet)this);
        this.zoneID = reader.getNextInt();
        this.zoneType = ZoneType.fromId(reader.getNextInt());
    }

    public PacketDeleteZone(int zoneID, ZoneType zoneType) {
        this.zoneID = zoneID;
        this.zoneType = zoneType;
        PacketWriter writer = new PacketWriter((Packet)this);
        writer.putNextInt(zoneID);
        writer.putNextInt(zoneType.getId());
    }

    /**
     * Legacy constructor for backward compatibility.
     * @deprecated Use {@link #PacketDeleteZone(int, ZoneType)} instead
     */
    @Deprecated
    public PacketDeleteZone(int zoneID, boolean isProtectedZone) {
        this(zoneID, isProtectedZone ? ZoneType.PROTECTED : ZoneType.PVP);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            // Validate using ZoneAPI
            medievalsim.util.ZoneAPI.ZoneContext ctx = medievalsim.util.ZoneAPI.forClient(client)
                .withPacketName("PacketDeleteZone")
                .requireZoneByType(this.zoneID, this.zoneType)
                .build();
            if (!ctx.isValid()) return;

            AdminZone zone = ctx.getAdminZone();
            
            // Defensive null check: zone could have been deleted by another thread
            if (zone == null) {
                ModLogger.warn("Zone %d already deleted, ignoring delete request from %s", this.zoneID, client.getName());
                return;
            }

            // Remove barriers for PvP zones
            if (zone instanceof PvPZone) {
                ((PvPZone)zone).removeBarriers(ctx.getLevel());
            }

            // Handle players currently in PvP zone
            if (this.zoneType == ZoneType.PVP) {
                long serverTime = server.world.worldEntity.getTime();
                for (ServerClient playerClient : server.getClients()) {
                    PvPZoneTracker.PlayerPvPState state = PvPZoneTracker.getPlayerState(playerClient);
                    if (state.getCurrentZoneID() != this.zoneID) continue;
                    PvPZoneTracker.exitZone(playerClient, serverTime);
                    if (playerClient.pvpEnabled && !server.world.settings.forcedPvP) {
                        playerClient.pvpEnabled = false;
                        server.network.sendToAllClients((Packet)new PacketPlayerPvP(playerClient.slot, false));
                    }
                    playerClient.sendChatMessage(necesse.engine.localization.Localization.translate("message", "zone.pvp.deleted"));
                }
            }

            // Delete zone based on type
            switch (this.zoneType) {
                case PVP:
                    ZoneHelper.deletePvPZone(ctx.getLevel(), this.zoneID, client);
                    break;
                case GUILD:
                    ZoneHelper.deleteGuildZone(ctx.getLevel(), this.zoneID, client);
                    break;
                default:
                    ZoneHelper.deleteProtectedZone(ctx.getLevel(), this.zoneID, client);
                    break;
            }
            
            ModLogger.info("Deleted zone " + this.zoneID + " (" + zone.name + ") by " + client.getName());
            server.network.sendToAllClients((Packet)new PacketZoneRemoved(this.zoneID, this.zoneType));
            // Defer saving to the resolver/autosave to avoid heavy synchronous compression during packet processing
            
        } catch (IllegalArgumentException e) {
            // Validation failure - send user-friendly message
            client.sendChatMessage(e.getMessage());
            ModLogger.warn("Invalid input in PacketDeleteZone from %s: %s", client.getName(), e.getMessage());
        } catch (Exception e) {
            String errorMsg = medievalsim.util.ErrorMessageBuilder.buildOperationFailedMessage(
                "Zone deletion",
                "An unexpected error occurred. Please contact an administrator."
            );
            client.sendChatMessage(errorMsg);
            ModLogger.error("Exception in PacketDeleteZone.processServer", e);
        }
    }
}

