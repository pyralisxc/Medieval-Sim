package medievalsim.guilds;

/**
 * Guild membership ranks with hierarchical permission levels.
 * Higher level = more permissions.
 */
public enum GuildRank {
    RECRUIT(1),     // New members, limited permissions
    MEMBER(2),      // Standard members
    OFFICER(3),     // Management permissions
    LEADER(4);      // Full control

    public final int level;

    GuildRank(int level) {
        this.level = level;
    }

    /**
     * Get the numeric level of this rank.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Check if this rank has at least the specified rank level.
     */
    public boolean hasRank(GuildRank required) {
        return this.level >= required.level;
    }

    /**
     * Get rank from level number.
     */
    public static GuildRank fromLevel(int level) {
        for (GuildRank rank : values()) {
            if (rank.level == level) return rank;
        }
        return RECRUIT;
    }

    /**
     * Get display name for UI.
     */
    public String getDisplayName() {
        return switch (this) {
            case LEADER -> "Leader";
            case OFFICER -> "Officer";
            case MEMBER -> "Member";
            case RECRUIT -> "Recruit";
        };
    }

    /**
     * Get the next rank up (promotion).
     * @return The promoted rank, or null if already at OFFICER (use transferLeadership for LEADER).
     */
    public GuildRank getPromotedRank() {
        return switch (this) {
            case RECRUIT -> MEMBER;
            case MEMBER -> OFFICER;
            case OFFICER, LEADER -> null; // Can't promote beyond Officer via normal promotion
        };
    }

    /**
     * Get the next rank down (demotion).
     * @return The demoted rank, or null if already at RECRUIT.
     */
    public GuildRank getDemotedRank() {
        return switch (this) {
            case LEADER -> OFFICER;
            case OFFICER -> MEMBER;
            case MEMBER -> RECRUIT;
            case RECRUIT -> null; // Can't demote below Recruit
        };
    }
}
