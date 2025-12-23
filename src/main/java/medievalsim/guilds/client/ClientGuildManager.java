package medievalsim.guilds.client;

import medievalsim.guilds.GuildRank;
import medievalsim.packets.PacketGuildBannersResponse;
import medievalsim.util.ModLogger;
import necesse.engine.network.client.Client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Client-side manager for tracking the local player's guild state.
 * Provides event notifications to subscribed UI components for auto-refresh.
 * 
 * This is a singleton per client session - it tracks:
 * - Current guild membership (ID, name, rank)
 * - Pending invitations
 * - Event subscriptions for UI refresh
 */
public class ClientGuildManager {
    
    private static ClientGuildManager instance;
    
    // Current guild state
    private int currentGuildID = -1;
    private String currentGuildName = "";
    private GuildRank currentRank = null;
    
    // Event listeners for UI refresh
    private final List<GuildEventListener> listeners = new CopyOnWriteArrayList<>();
    
    // Pending invitation (if any)
    private PendingInvite pendingInvite = null;
    
    // Banner cache - guildID -> list of banners
    private final Map<Integer, List<PacketGuildBannersResponse.BannerInfo>> bannerCache = new ConcurrentHashMap<>();
    
    private ClientGuildManager() {
        // Private constructor for singleton
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized ClientGuildManager get() {
        if (instance == null) {
            instance = new ClientGuildManager();
        }
        return instance;
    }
    
    /**
     * Reset state (call on disconnect/logout).
     */
    public static void reset() {
        if (instance != null) {
            instance.currentGuildID = -1;
            instance.currentGuildName = "";
            instance.currentRank = null;
            instance.pendingInvite = null;
            instance.listeners.clear();
            instance.bannerCache.clear();
        }
    }
    
    // ==================== Guild State ====================
    
    public boolean isInGuild() {
        return currentGuildID > 0;
    }
    
    public int getCurrentGuildID() {
        return currentGuildID;
    }
    
    public String getCurrentGuildName() {
        return currentGuildName;
    }
    
    public GuildRank getCurrentRank() {
        return currentRank;
    }
    
    /**
     * Called when player joins a guild (via create, invite accept, or join public).
     */
    public void setCurrentGuild(int guildID, String guildName, GuildRank rank) {
        int oldGuildID = this.currentGuildID;
        this.currentGuildID = guildID;
        this.currentGuildName = guildName;
        this.currentRank = rank;
        
        ModLogger.debug("ClientGuildManager: Joined guild %d (%s) as %s", 
            guildID, guildName, rank != null ? rank.getDisplayName() : "unknown");
        
        if (oldGuildID != guildID) {
            notifyGuildJoined(guildID, guildName, rank);
        }
    }
    
    /**
     * Called when player leaves or is kicked from guild.
     */
    public void clearCurrentGuild(int guildID, String guildName, boolean wasKicked, String reason) {
        if (this.currentGuildID == guildID || guildID == -1) {
            int oldID = this.currentGuildID;
            String oldName = this.currentGuildName;
            
            this.currentGuildID = -1;
            this.currentGuildName = "";
            this.currentRank = null;
            
            ModLogger.debug("ClientGuildManager: Left guild %d (%s), kicked=%b", 
                oldID, oldName, wasKicked);
            
            notifyGuildLeft(oldID, oldName, wasKicked, reason);
        }
    }
    
    /**
     * Called when player's rank changes.
     */
    public void updateRank(int guildID, GuildRank newRank) {
        if (this.currentGuildID == guildID) {
            GuildRank oldRank = this.currentRank;
            this.currentRank = newRank;
            
            ModLogger.debug("ClientGuildManager: Rank changed from %s to %s", 
                oldRank != null ? oldRank.getDisplayName() : "none",
                newRank != null ? newRank.getDisplayName() : "none");
            
            notifyRankChanged(guildID, oldRank, newRank);
        }
    }
    
    // ==================== Pending Invitations ====================
    
    public void setPendingInvite(int guildID, String guildName, long senderAuth, String senderName) {
        this.pendingInvite = new PendingInvite(guildID, guildName, senderAuth, senderName);
        notifyInviteReceived(guildID, guildName, senderName);
    }
    
    public void clearPendingInvite() {
        this.pendingInvite = null;
    }
    
    public PendingInvite getPendingInvite() {
        return pendingInvite;
    }
    
    // ==================== Event Listeners ====================
    
    public void addListener(GuildEventListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            ModLogger.debug("ClientGuildManager: Added listener %s", listener.getClass().getSimpleName());
        }
    }
    
    public void removeListener(GuildEventListener listener) {
        listeners.remove(listener);
        ModLogger.debug("ClientGuildManager: Removed listener %s", listener.getClass().getSimpleName());
    }
    
    // ==================== Notification Methods ====================
    
    private void notifyGuildJoined(int guildID, String guildName, GuildRank rank) {
        for (GuildEventListener listener : listeners) {
            try {
                listener.onGuildJoined(guildID, guildName, rank);
            } catch (Exception e) {
                ModLogger.error("Error notifying listener of guild join", e);
            }
        }
    }
    
    private void notifyGuildLeft(int guildID, String guildName, boolean wasKicked, String reason) {
        for (GuildEventListener listener : listeners) {
            try {
                listener.onGuildLeft(guildID, guildName, wasKicked, reason);
            } catch (Exception e) {
                ModLogger.error("Error notifying listener of guild leave", e);
            }
        }
    }
    
    private void notifyRankChanged(int guildID, GuildRank oldRank, GuildRank newRank) {
        for (GuildEventListener listener : listeners) {
            try {
                listener.onRankChanged(guildID, oldRank, newRank);
            } catch (Exception e) {
                ModLogger.error("Error notifying listener of rank change", e);
            }
        }
    }
    
    private void notifyInviteReceived(int guildID, String guildName, String senderName) {
        for (GuildEventListener listener : listeners) {
            try {
                listener.onInviteReceived(guildID, guildName, senderName);
            } catch (Exception e) {
                ModLogger.error("Error notifying listener of invite", e);
            }
        }
    }
    
    /**
     * Notify listeners that a member joined/left/rank changed (for UI refresh).
     * This is for OTHER members, not the local player.
     */
    public void notifyMemberUpdate(int guildID, long memberAuth, GuildMemberUpdateType updateType) {
        if (this.currentGuildID != guildID) return;
        
        for (GuildEventListener listener : listeners) {
            try {
                listener.onMemberUpdate(guildID, memberAuth, updateType);
            } catch (Exception e) {
                ModLogger.error("Error notifying listener of member update", e);
            }
        }
    }
    
    // ==================== Banner Cache ====================
    
    /**
     * Update the cached banner list for a guild.
     * Called when PacketGuildBannersResponse is received.
     */
    public void updateGuildBanners(int guildID, List<PacketGuildBannersResponse.BannerInfo> banners) {
        bannerCache.put(guildID, new ArrayList<>(banners));
        ModLogger.debug("ClientGuildManager: Updated banner cache for guild %d with %d banners", 
            guildID, banners.size());
        
        // Notify listeners of data change
        for (GuildEventListener listener : listeners) {
            try {
                listener.onGuildDataChanged(guildID);
            } catch (Exception e) {
                ModLogger.error("Error notifying listener of banner update", e);
            }
        }
    }
    
    /**
     * Get cached banners for a guild.
     * @return The banner list, or null if not cached.
     */
    public List<PacketGuildBannersResponse.BannerInfo> getCachedBanners(int guildID) {
        List<PacketGuildBannersResponse.BannerInfo> cached = bannerCache.get(guildID);
        return cached != null ? new ArrayList<>(cached) : null;
    }
    
    /**
     * Clear banner cache for a guild.
     */
    public void clearBannerCache(int guildID) {
        bannerCache.remove(guildID);
    }
    
    // ==================== Inner Classes ====================
    
    public static class PendingInvite {
        public final int guildID;
        public final String guildName;
        public final long senderAuth;
        public final String senderName;
        public final long timestamp;
        
        public PendingInvite(int guildID, String guildName, long senderAuth, String senderName) {
            this.guildID = guildID;
            this.guildName = guildName;
            this.senderAuth = senderAuth;
            this.senderName = senderName;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public enum GuildMemberUpdateType {
        JOINED,
        LEFT,
        KICKED,
        RANK_CHANGED
    }
}
