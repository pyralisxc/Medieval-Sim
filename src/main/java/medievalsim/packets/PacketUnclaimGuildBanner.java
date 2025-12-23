/*
 * PacketUnclaimGuildBanner - Request to unclaim/delete a guild banner
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
 * Client -> Server packet to unclaim/delete a guild banner.
 * Per docs: only the placer or leader can unclaim.
 */
public class PacketUnclaimGuildBanner extends Packet {
    
    public int guildID;
    public int tileX;
    public int tileY;
    // Note: We use the player's current level for lookup
    
    public PacketUnclaimGuildBanner(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.tileX = reader.getNextInt();
        this.tileY = reader.getNextInt();
    }
    
    public PacketUnclaimGuildBanner(int guildID, int tileX, int tileY) {
        this.guildID = guildID;
        this.tileX = tileX;
        this.tileY = tileY;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextInt(tileX);
        writer.putNextInt(tileY);
    }
    
    /**
     * Process on server - validate and unclaim banner.
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
            
            // Find the banner object entity in player's current level
            Level level = client.playerMob != null ? client.playerMob.getLevel() : null;
            if (level == null) {
                client.sendChatMessage("Cannot find your current location");
                return;
            }
            
            ObjectEntity entity = level.entityManager.getObjectEntity(this.tileX, this.tileY);
            if (!(entity instanceof GuildTeleportBannerObjectEntity banner)) {
                client.sendChatMessage("No guild banner at that location");
                return;
            }
            
            if (!banner.belongsToGuild(this.guildID)) {
                client.sendChatMessage("That banner does not belong to this guild");
                return;
            }
            
            // Permission check: placer or leader can unclaim
            // For now, only leaders can unclaim (TODO: track placer auth in banner entity)
            if (playerRank.level < GuildRank.LEADER.level) {
                client.sendChatMessage("Only the guild leader can unclaim banners");
                return;
            }
            
            // Clear guild ownership (keeps the banner object but removes guild association)
            banner.setOwnerGuildID(-1);
            banner.setCustomName(null);
            
            client.sendChatMessage("Banner unclaimed from guild");
            ModLogger.debug("Player %d unclaimed banner at (%d,%d) from guild %d",
                playerAuth, this.tileX, this.tileY, this.guildID);
            
        } catch (Exception e) {
            ModLogger.error("Error processing PacketUnclaimGuildBanner", e);
            client.sendChatMessage("Failed to unclaim banner");
        }
    }
}
