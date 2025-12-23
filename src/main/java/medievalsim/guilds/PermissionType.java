package medievalsim.guilds;

/**
 * Permission types for guild actions.
 * Each permission can be assigned a minimum rank by the guild leader.
 */
public enum PermissionType {
    // Building & Breaking
    BUILD("Build blocks", GuildRank.OFFICER),
    BREAK("Break blocks", GuildRank.OFFICER),
    
    // Storage Access
    ACCESS_STORAGE("Access settlement storage", GuildRank.RECRUIT),
    ACCESS_GUILD_BANK("Access guild bank", GuildRank.MEMBER),
    DEPOSIT_GUILD_BANK("Deposit to guild bank", GuildRank.RECRUIT),
    WITHDRAW_GUILD_BANK("Withdraw from guild bank", GuildRank.MEMBER),
    BANK_DEPOSIT("Deposit to guild bank", GuildRank.RECRUIT), // Alias for container compatibility
    BANK_WITHDRAW("Withdraw from guild bank", GuildRank.MEMBER), // Alias for container compatibility
    
    // Territory
    CLAIM_TERRITORY("Claim territory for guild", GuildRank.OFFICER),
    
    // Treasury
    DEPOSIT_TREASURY("Deposit gold to treasury", GuildRank.RECRUIT),
    WITHDRAW_TREASURY("Withdraw gold from treasury", GuildRank.OFFICER),
    
    // Research
    VIEW_RESEARCH("View research tree", GuildRank.RECRUIT),
    DONATE_RESEARCH("Donate items to research", GuildRank.RECRUIT),
    MODIFY_RESEARCH("Modify research priorities", GuildRank.OFFICER),
    SET_TREASURY_THRESHOLD("Set scientist treasury minimum", GuildRank.OFFICER),
    
    // Membership
    INVITE_MEMBER("Invite new members", GuildRank.MEMBER),
    KICK_MEMBER("Kick members", GuildRank.OFFICER),
    PROMOTE_DEMOTE("Promote/demote members", GuildRank.LEADER),
    
    // Settings & Admin
    EDIT_GUILD_INFO("Edit guild name/description", GuildRank.LEADER),
    EDIT_CREST("Edit guild crest", GuildRank.LEADER),
    SET_TAX_RATE("Set guild tax rate", GuildRank.LEADER),
    MODIFY_PERMISSIONS("Modify permission settings", GuildRank.LEADER),
    PURCHASE_PLOTS("Purchase land plots", GuildRank.LEADER),
    DISBAND_GUILD("Disband guild", GuildRank.LEADER),
    
    // Objects & Interactions
    USE_TELEPORT_STAND("Use guild teleport stand", GuildRank.RECRUIT),
    PLACE_GUILD_OBJECTS("Place guild objects", GuildRank.OFFICER),
    USE_CAULDRON("Use guild cauldron", GuildRank.RECRUIT),
    
    // Zone access
    ACCESS_ZONE("Access guild zone", GuildRank.RECRUIT),
    INTERACT("Interact with objects in zone", GuildRank.RECRUIT);

    private final String displayName;
    private final GuildRank defaultMinimumRank;

    PermissionType(String displayName, GuildRank defaultMinimumRank) {
        this.displayName = displayName;
        this.defaultMinimumRank = defaultMinimumRank;
    }

    public String getDisplayName() {
        return displayName;
    }

    public GuildRank getDefaultMinimumRank() {
        return defaultMinimumRank;
    }

    /**
     * Check if this permission can be customized by the leader.
     * Some permissions (like PROMOTE_DEMOTE, DISBAND_GUILD) are always Leader-only.
     */
    public boolean isCustomizable() {
        return switch (this) {
            case PROMOTE_DEMOTE, DISBAND_GUILD, MODIFY_PERMISSIONS -> false;
            default -> true;
        };
    }
}
