package medievalsim.guilds.client;

import medievalsim.guilds.GuildRank;
import medievalsim.guilds.client.ClientGuildManager.GuildMemberUpdateType;

/**
 * Interface for UI components that want to receive guild event notifications.
 * Implement this interface and register with ClientGuildManager to get auto-refresh.
 * 
 * Remember to call ClientGuildManager.get().removeListener(this) when the UI closes!
 */
public interface GuildEventListener {
    
    /**
     * Called when the local player joins a guild.
     * @param guildID The guild ID
     * @param guildName The guild name
     * @param rank The player's initial rank
     */
    default void onGuildJoined(int guildID, String guildName, GuildRank rank) {}
    
    /**
     * Called when the local player leaves or is kicked from their guild.
     * @param guildID The guild ID that was left
     * @param guildName The guild name
     * @param wasKicked True if kicked, false if voluntarily left
     * @param reason Kick reason (empty if not kicked)
     */
    default void onGuildLeft(int guildID, String guildName, boolean wasKicked, String reason) {}
    
    /**
     * Called when the local player's rank changes.
     * @param guildID The guild ID
     * @param oldRank Previous rank
     * @param newRank New rank
     */
    default void onRankChanged(int guildID, GuildRank oldRank, GuildRank newRank) {}
    
    /**
     * Called when the local player receives a guild invitation.
     * @param guildID The inviting guild's ID
     * @param guildName The guild name
     * @param senderName Who sent the invite
     */
    default void onInviteReceived(int guildID, String guildName, String senderName) {}
    
    /**
     * Called when another member's state changes (join, leave, kick, rank change).
     * Use this for UI refresh when viewing member lists.
     * @param guildID The guild ID
     * @param memberAuth The affected member's auth
     * @param updateType What happened to the member
     */
    default void onMemberUpdate(int guildID, long memberAuth, GuildMemberUpdateType updateType) {}
    
    /**
     * Called when guild data has changed and a full refresh is recommended.
     * This is a catch-all for any changes that don't fit the specific methods.
     * @param guildID The guild ID
     */
    default void onGuildDataChanged(int guildID) {}
}
