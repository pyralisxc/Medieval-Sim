/*
 * PacketRenameGuildBanner - Request to rename a guild banner
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
import necesse.level.maps.levelData.settlementData.LevelSettler;

/**
 * Client -> Server packet to rename a guild banner.
 * Per docs: only the placer or leader can rename.
 */
public class PacketRenameGuildBanner extends Packet {
    
    public int guildID;
    public int tileX;
    public int tileY;
    public String newName;
    // Note: We use the player's current level for lookup
    
    public PacketRenameGuildBanner(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.tileX = reader.getNextInt();
        this.tileY = reader.getNextInt();
        this.newName = reader.getNextString();
    }
    
    public PacketRenameGuildBanner(int guildID, int tileX, int tileY, String newName) {
        this.guildID = guildID;
        this.tileX = tileX;
        this.tileY = tileY;
        this.newName = newName;
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextInt(tileX);
        writer.putNextInt(tileY);
        writer.putNextString(newName);
    }
    
    /**
     * Process on server - validate and rename banner.
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
            
            // Validate name
            if (this.newName == null || this.newName.trim().isEmpty()) {
                client.sendChatMessage("Banner name cannot be empty");
                return;
            }
            if (this.newName.length() > 32) {
                client.sendChatMessage("Banner name too long (max 32 characters)");
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
            
            // Permission check: placer or leader can rename
            // For now, allow any member to rename (TODO: track placer auth in banner entity)
            if (playerRank.level < GuildRank.MEMBER.level) {
                client.sendChatMessage("You don't have permission to rename banners");
                return;
            }
            
            // Rename
            banner.setCustomName(this.newName.trim());
            
            client.sendChatMessage("Banner renamed to: " + this.newName.trim());
            ModLogger.debug("Player %d renamed banner at (%d,%d) in guild %d to '%s'",
                playerAuth, this.tileX, this.tileY, this.guildID, this.newName);
            
        } catch (Exception e) {
            ModLogger.error("Error processing PacketRenameGuildBanner", e);
            client.sendChatMessage("Failed to rename banner");
        }
    }
}
