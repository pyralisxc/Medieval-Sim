/*
 * GuildNotification - Represents a notification for guild events
 * Part of Medieval Sim Mod guild management system.
 * Per docs: notifications include invites, guild notices, admin messages.
 */
package medievalsim.guilds.notifications;

/**
 * Represents a guild notification that appears in a player's inbox.
 * Notifications have a type, message, and optional action data.
 */
public class GuildNotification {
    
    /**
     * Types of guild notifications.
     */
    public enum NotificationType {
        GUILD_INVITE,       // Invitation to join a guild
        GUILD_NOTICE,       // General guild announcement
        RANK_CHANGED,       // Player's rank was changed
        MEMBER_JOINED,      // New member joined the guild
        MEMBER_LEFT,        // Member left the guild
        RESEARCH_COMPLETE,  // Research project completed
        ADMIN_MESSAGE,      // Message from server admin
        TREASURY_UPDATE,    // Significant treasury change
        CUSTOM              // Custom notification type
    }
    
    private final long notificationID;
    private final NotificationType type;
    private final String title;
    private final String message;
    private final long timestamp;
    private final int guildID;
    private boolean read;
    private final String actionData;
    private final long ttlMillis;  // Time-to-live in milliseconds (0 = never expires)
    
    public GuildNotification(
            long notificationID,
            NotificationType type,
            String title,
            String message,
            int guildID,
            String actionData,
            long ttlMillis) {
        this.notificationID = notificationID;
        this.type = type;
        this.title = title;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.guildID = guildID;
        this.read = false;
        this.actionData = actionData;
        this.ttlMillis = ttlMillis;
    }
    
    /**
     * Constructor for loading with a specific timestamp.
     */
    public GuildNotification(
            long notificationID,
            NotificationType type,
            String title,
            String message,
            int guildID,
            String actionData,
            long ttlMillis,
            long timestamp) {
        this.notificationID = notificationID;
        this.type = type;
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.guildID = guildID;
        this.read = false;
        this.actionData = actionData;
        this.ttlMillis = ttlMillis;
    }
    
    public GuildNotification(
            long notificationID,
            NotificationType type,
            String title,
            String message,
            int guildID) {
        this(notificationID, type, title, message, guildID, null, 0);
    }
    
    // === Getters ===
    
    public long getNotificationID() { return notificationID; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
    public int getGuildID() { return guildID; }
    public boolean isRead() { return read; }
    public String getActionData() { return actionData; }
    public long getTtlMillis() { return ttlMillis; }
    
    public void markRead() { this.read = true; }
    
    /**
     * Check if this notification has expired.
     */
    public boolean isExpired() {
        if (ttlMillis <= 0) return false;
        return System.currentTimeMillis() > timestamp + ttlMillis;
    }
    
    /**
     * Create an invite notification.
     */
    public static GuildNotification createInvite(long notifID, int guildID, String guildName, String inviterName) {
        return new GuildNotification(
            notifID,
            NotificationType.GUILD_INVITE,
            "Guild Invitation",
            String.format("%s has invited you to join %s", inviterName, guildName),
            guildID,
            String.valueOf(guildID),  // Action data = guild ID to accept
            7 * 24 * 60 * 60 * 1000L  // 7 day TTL
        );
    }
    
    /**
     * Create a rank changed notification.
     */
    public static GuildNotification createRankChanged(long notifID, int guildID, String guildName, String newRank) {
        return new GuildNotification(
            notifID,
            NotificationType.RANK_CHANGED,
            "Rank Changed",
            String.format("Your rank in %s has been changed to %s", guildName, newRank),
            guildID
        );
    }
    
    /**
     * Create a research complete notification.
     */
    public static GuildNotification createResearchComplete(long notifID, int guildID, String guildName, String researchName) {
        return new GuildNotification(
            notifID,
            NotificationType.RESEARCH_COMPLETE,
            "Research Complete",
            String.format("%s has completed research: %s", guildName, researchName),
            guildID
        );
    }
    
    /**
     * Create a guild notice.
     */
    public static GuildNotification createGuildNotice(long notifID, int guildID, String title, String message) {
        return new GuildNotification(
            notifID,
            NotificationType.GUILD_NOTICE,
            title,
            message,
            guildID
        );
    }
}
