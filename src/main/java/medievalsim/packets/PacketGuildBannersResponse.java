/*
 * PacketGuildBannersResponse - Server response with guild banner list
 * Part of Medieval Sim Mod guild system.
 */
package medievalsim.packets;

import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client packet with the list of banners for a guild.
 */
public class PacketGuildBannersResponse extends Packet {
    
    public int guildID;
    public List<BannerInfo> banners;
    
    /**
     * Banner information structure for transmission.
     */
    public static class BannerInfo {
        public final int bannerID;
        public final String name;
        public final String placedByName;
        public final long placedByAuth;
        public final int levelID;
        public final int tileX;
        public final int tileY;
        public final boolean canRename;
        public final boolean canUnclaim;
        
        public BannerInfo(int bannerID, String name, String placedByName, long placedByAuth,
                         int levelID, int tileX, int tileY, boolean canRename, boolean canUnclaim) {
            this.bannerID = bannerID;
            this.name = name;
            this.placedByName = placedByName;
            this.placedByAuth = placedByAuth;
            this.levelID = levelID;
            this.tileX = tileX;
            this.tileY = tileY;
            this.canRename = canRename;
            this.canUnclaim = canUnclaim;
        }
    }
    
    public PacketGuildBannersResponse(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        int count = reader.getNextInt();
        this.banners = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            banners.add(new BannerInfo(
                reader.getNextInt(),    // bannerID
                reader.getNextString(), // name
                reader.getNextString(), // placedByName
                reader.getNextLong(),   // placedByAuth
                reader.getNextInt(),    // levelID
                reader.getNextInt(),    // tileX
                reader.getNextInt(),    // tileY
                reader.getNextBoolean(), // canRename
                reader.getNextBoolean()  // canUnclaim
            ));
        }
    }
    
    public PacketGuildBannersResponse(int guildID, List<BannerInfo> banners) {
        this.guildID = guildID;
        this.banners = banners != null ? banners : new ArrayList<>();
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextInt(this.banners.size());
        for (BannerInfo b : this.banners) {
            writer.putNextInt(b.bannerID);
            writer.putNextString(b.name);
            writer.putNextString(b.placedByName);
            writer.putNextLong(b.placedByAuth);
            writer.putNextInt(b.levelID);
            writer.putNextInt(b.tileX);
            writer.putNextInt(b.tileY);
            writer.putNextBoolean(b.canRename);
            writer.putNextBoolean(b.canUnclaim);
        }
    }
    
    public int getGuildID() { return guildID; }
    public List<BannerInfo> getBanners() { return banners; }
    
    /**
     * Process on client - update UI with banner list.
     */
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        try {
            ModLogger.debug("Received banner list for guild %d: %d banners", 
                this.guildID, this.banners.size());
            
            // The GuildInfoPanelForm will handle this via listener pattern or direct callback
            // Store in client guild manager
            medievalsim.guilds.client.ClientGuildManager cgm = medievalsim.guilds.client.ClientGuildManager.get();
            cgm.updateGuildBanners(this.guildID, this.banners);
            
        } catch (Exception e) {
            ModLogger.error("Error processing PacketGuildBannersResponse", e);
        }
    }
}
