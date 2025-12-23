/*
 * PacketTeleportToGuildBanner - Request to teleport to a guild banner
 * Part of Medieval Sim Mod guild system.
 */
package medievalsim.packets;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.GuildRank;
import medievalsim.guilds.objects.GuildTeleportBannerObjectEntity;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;

/**
 * Client -> Server packet to teleport to a guild banner location.
 */
public class PacketTeleportToGuildBanner extends Packet {
    
    public int guildID;
    public int targetTileX;
    public int targetTileY;
    // Note: For cross-level teleport we'd need level identifier serialization
    // For now we only support same-level teleport
    
    public PacketTeleportToGuildBanner(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.targetTileX = reader.getNextInt();
        this.targetTileY = reader.getNextInt();
    }
    
    public PacketTeleportToGuildBanner(int guildID, int targetTileX, int targetTileY) {
        this.guildID = guildID;
        this.targetTileX = targetTileX;
        this.targetTileY = targetTileY;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextInt(targetTileX);
        writer.putNextInt(targetTileY);
    }
    
    /**
     * Process on server - validate and teleport player.
     */
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            GuildManager gm = GuildManager.get(server.world);
            if (gm == null) {
                client.sendChatMessage("Guild system not available");
                return;
            }
            
            GuildData guild = gm.getGuild(this.guildID);
            if (guild == null) {
                client.sendChatMessage("Guild not found");
                return;
            }
            
            long playerAuth = client.authentication;
            GuildRank playerRank = guild.getMemberRank(playerAuth);
            
            if (playerRank == null) {
                client.sendChatMessage("You are not a member of this guild");
                return;
            }
            
            // For now, only support same-level teleport
            // Find the target banner in player's current level
            Level level = client.playerMob != null ? client.playerMob.getLevel() : null;
            if (level == null) {
                client.sendChatMessage("Cannot find your current location");
                return;
            }
            
            ObjectEntity entity = level.entityManager.getObjectEntity(this.targetTileX, this.targetTileY);
            if (!(entity instanceof GuildTeleportBannerObjectEntity banner)) {
                client.sendChatMessage("No guild banner at destination");
                return;
            }
            
            if (!banner.belongsToGuild(this.guildID)) {
                client.sendChatMessage("That banner does not belong to your guild");
                return;
            }
            
            // Calculate world position from tile position
            int worldX = this.targetTileX * 32 + 16;
            int worldY = this.targetTileY * 32 + 16;
            
            // Teleport the player
            if (client.playerMob != null) {
                client.playerMob.setPos(worldX, worldY, true);
                client.sendChatMessage("Teleported to: " + banner.getDisplayName());
                
                ModLogger.debug("Player %d teleported to banner at (%d,%d)",
                    playerAuth, this.targetTileX, this.targetTileY);
            }
            
        } catch (Exception e) {
            ModLogger.error("Error processing PacketTeleportToGuildBanner", e);
            client.sendChatMessage("Failed to teleport");
        }
    }
}
