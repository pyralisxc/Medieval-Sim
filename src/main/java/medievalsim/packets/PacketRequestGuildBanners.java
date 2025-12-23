/*
 * PacketRequestGuildBanners - Request list of guild banners
 * Part of Medieval Sim Mod guild system.
 */
package medievalsim.packets;

import medievalsim.guilds.GuildManager;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

/**
 * Client -> Server packet requesting the list of banners for a specific guild.
 * Response is PacketGuildBannersResponse.
 */
public class PacketRequestGuildBanners extends Packet {
    
    public int guildID;
    
    public PacketRequestGuildBanners(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
    }
    
    public PacketRequestGuildBanners(int guildID) {
        this.guildID = guildID;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
    }
    
    /**
     * Process on server - gather banner data and send response.
     */
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            GuildManager gm = GuildManager.get(server.world);
            if (gm == null) {
                ModLogger.warn("GuildManager not found processing PacketRequestGuildBanners");
                return;
            }
            
            long playerAuth = client.authentication;
            
            // Verify player is a member of this guild
            var guild = gm.getGuild(this.guildID);
            if (guild == null) {
                ModLogger.warn("Guild %d not found for banner request", this.guildID);
                return;
            }
            
            if (!guild.isMember(playerAuth)) {
                ModLogger.warn("Player %d not member of guild %d, cannot view banners", playerAuth, this.guildID);
                return;
            }
            
            // Find all banners for this guild across all loaded levels
            // For now, send an empty placeholder response
            // Real implementation would iterate world levels finding GuildTeleportBannerObjectEntity
            PacketGuildBannersResponse response = new PacketGuildBannersResponse(
                this.guildID,
                new java.util.ArrayList<>() // Placeholder - actual implementation will gather from world
            );
            
            client.sendPacket(response);
            
        } catch (Exception e) {
            ModLogger.error("Error processing PacketRequestGuildBanners", e);
        }
    }
}
