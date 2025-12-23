package medievalsim.guilds;

/**
 * Audit log entry for guild actions.
 * Used for tracking deposits, withdrawals, membership changes, etc.
 */
public class GuildAuditEntry {
    
    public enum Action {
        GUILD_CREATED("Guild Created"),
        GUILD_DISBANDED("Guild Disbanded"),
        MEMBER_JOINED("Member Joined"),
        MEMBER_LEFT("Member Left"),
        MEMBER_KICKED("Member Kicked"),
        MEMBER_PROMOTED("Member Promoted"),
        MEMBER_DEMOTED("Member Demoted"),
        LEADERSHIP_TRANSFERRED("Leadership Transferred"),
        TREASURY_DEPOSIT("Treasury Deposit"),
        TREASURY_WITHDRAW("Treasury Withdrawal"),
        BANK_DEPOSIT("Bank Deposit"),
        BANK_WITHDRAW("Bank Withdrawal"),
        RESEARCH_STARTED("Research Started"),
        RESEARCH_COMPLETED("Research Completed"),
        RESEARCH_DONATION("Research Donation"),
        SETTINGS_CHANGED("Settings Changed"),
        CREST_CHANGED("Crest Changed"),
        PLOT_PURCHASED("Plot Purchased"),
        PLOT_SOLD("Plot Sold"),
        TAX_COLLECTED("Tax Collected"),
        ZONE_CLAIMED("Zone Claimed"),
        ZONE_EXPANDED("Zone Expanded"),
        ZONE_SHRUNK("Zone Shrunk");

        private final String displayName;

        Action(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final Action action;
    private final long playerAuth;
    private final String details;
    private final long timestamp;

    public GuildAuditEntry(Action action, long playerAuth, String details) {
        this.action = action;
        this.playerAuth = playerAuth;
        this.details = details;
        this.timestamp = System.currentTimeMillis();
    }

    public GuildAuditEntry(Action action, long playerAuth, String details, long timestamp) {
        this.action = action;
        this.playerAuth = playerAuth;
        this.details = details;
        this.timestamp = timestamp;
    }

    public Action getAction() { return action; }
    public long getPlayerAuth() { return playerAuth; }
    public String getDetails() { return details; }
    public long getTimestamp() { return timestamp; }

    /**
     * Format timestamp as relative time string.
     */
    public String getRelativeTimeString() {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + " day" + (days > 1 ? "s" : "") + " ago";
        if (hours > 0) return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        if (minutes > 0) return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        return "just now";
    }

    @Override
    public String toString() {
        return "[" + action.getDisplayName() + "] " + details + " (" + getRelativeTimeString() + ")";
    }
}
