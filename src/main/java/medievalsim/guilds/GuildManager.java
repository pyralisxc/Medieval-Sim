package medievalsim.guilds;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.world.World;
import necesse.engine.world.worldData.WorldData;
import necesse.entity.mobs.PlayerMob;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * World-level singleton managing all guilds.
 * Handles guild lifecycle, persistence, and cross-guild operations.
 */
public class GuildManager extends WorldData {
    
    public static final String DATA_KEY = "medievalsimguilds";
    private static final int SAVE_VERSION = 1;

    // All guilds indexed by ID
    private final Map<Integer, GuildData> guilds = new ConcurrentHashMap<>();

    // Player data indexed by auth
    private final Map<Long, PlayerGuildData> playerData = new ConcurrentHashMap<>();

    // Guild name index for fast lookup/validation
    private final Map<String, Integer> guildNameIndex = new ConcurrentHashMap<>();

    // ID counter
    private final AtomicInteger nextGuildID = new AtomicInteger(1);

    // Research tick tracking
    private long lastResearchTick = 0;
    private static final long RESEARCH_TICK_INTERVAL = 60 * 60 * 1000; // 1 hour in ms

    public GuildManager() {
        super();
    }

    /**
     * Get GuildManager instance from world.
     */
    public static GuildManager get(World world) {
        if (world == null || world.worldEntity == null) return null;
        WorldData data = world.worldEntity.getWorldData(DATA_KEY);
        if (data instanceof GuildManager) {
            ModLogger.debug("GuildManager.get() - returning existing instance with %d guilds", ((GuildManager)data).guilds.size());
            return (GuildManager) data;
        }
        // Create if doesn't exist
        ModLogger.info("GuildManager.get() - creating NEW instance (no existing WorldData found)");
        GuildManager manager = new GuildManager();
        world.worldEntity.addWorldData(DATA_KEY, manager);
        return manager;
    }

    // === Guild Lifecycle ===

    /**
     * Create a new guild with default settings.
     * @return The new guild, or null if creation failed.
     */
    public GuildData createGuild(String name, long founderAuth) {
        return createGuild(name, founderAuth, "", true);
    }
    
    /**
     * Create a new guild with full settings.
     * @param name Guild name
     * @param founderAuth Player auth of founder
     * @param description Guild description
     * @param isPublic Whether guild is publicly visible
     * @return The new guild, or null if creation failed.
     */
    public GuildData createGuild(String name, long founderAuth, String description, boolean isPublic) {
        try {
            // Validate name
            if (!isValidGuildName(name)) {
                return null;
            }

            // Check if name is taken
            if (isGuildNameTaken(name)) {
                return null;
            }

            // Check player can create (not already leader, under max guilds)
            PlayerGuildData playerGD = getOrCreatePlayerData(founderAuth);
            if (!playerGD.canBecomeLeader()) {
                return null;
            }
            if (!playerGD.canJoinMoreGuilds(ModConfig.Guilds.maxGuildsPerPlayer)) {
                return null;
            }

            // Create guild
            int guildID = nextGuildID.getAndIncrement();
            GuildData guild = new GuildData(guildID, name, founderAuth);
            
            // Apply additional settings
            if (description != null && !description.isEmpty()) {
                guild.setDescription(description);
            }
            guild.setPublic(isPublic);
            
            guilds.put(guildID, guild);
            guildNameIndex.put(name.toLowerCase(), guildID);

            // Update player data
            playerGD.addMembership(guildID, GuildRank.LEADER);

            // Audit log
            guild.addAuditEntry(GuildAuditEntry.Action.GUILD_CREATED, founderAuth, "Guild created by founder");

            ModLogger.debug("Created guild '" + name + "' (ID: " + guildID + ") by player " + founderAuth + ", public=" + isPublic);
            return guild;
        } catch (Exception e) {
            ModLogger.error("Exception in createGuild", e);
            e.printStackTrace(System.err);
            throw e;
        }
    }

    /**
     * Disband a guild, distributing treasury to members.
     * @return Map of playerAuth -> gold received, or null if disband failed.
     */
    public Map<Long, Long> disbandGuild(int guildID, long requestingAuth) {
        GuildData guild = guilds.get(guildID);
        if (guild == null) return null;

        // Only leader can disband
        if (!guild.hasPermission(requestingAuth, PermissionType.DISBAND_GUILD)) {
            return null;
        }

        // Distribute treasury
        Map<Long, Long> distribution = guild.disbandDistributeTreasury();

        // Update all members' player data
        for (Long memberAuth : guild.getMembers().keySet()) {
            PlayerGuildData playerGD = playerData.get(memberAuth);
            if (playerGD != null) {
                playerGD.removeMembership(guildID);
            }
        }

        // Remove from indices
        guildNameIndex.remove(guild.getName().toLowerCase());
        guilds.remove(guildID);

        // Remove guild zones to avoid orphaned plots
        try {
            if (this.getServer() != null && this.getServer().world != null) {
                medievalsim.zones.service.ZoneHelper.removeAllGuildZonesForGuild(this.getServer(), guildID);
            }
        } catch (Exception e) {
            ModLogger.info("Failed to remove guild zones for disbanded guild %d: %s", guildID, e.getMessage());
        }

        // Audit and log
        ModLogger.debug("Disbanded guild '" + guild.getName() + "' (ID: " + guildID + ")");
        guild.addAuditEntry(GuildAuditEntry.Action.GUILD_DISBANDED, requestingAuth, "Guild disbanded");
        return distribution;
    }

    // === Membership ===

    /**
     * Add a player to a guild.
     */
    public boolean joinGuild(int guildID, long playerAuth, GuildRank rank) {
        GuildData guild = guilds.get(guildID);
        if (guild == null) return false;

        // Check player limits
        PlayerGuildData playerGD = getOrCreatePlayerData(playerAuth);
        if (!playerGD.canJoinMoreGuilds(ModConfig.Guilds.maxGuildsPerPlayer)) {
            return false;
        }

        // Check guild member limit
        if (guild.getMemberCount() >= ModConfig.Guilds.maxMembersPerGuild) {
            return false;
        }

        // Check leadership constraint
        if (rank == GuildRank.LEADER && !playerGD.canBecomeLeader()) {
            return false;
        }

        // Add to guild
        if (!guild.addMember(playerAuth, rank)) {
            return false;
        }

        // Update player data
        playerGD.addMembership(guildID, rank);

        guild.addAuditEntry(GuildAuditEntry.Action.MEMBER_JOINED, playerAuth, "Joined as " + rank.getDisplayName());
        return true;
    }

    /**
     * Remove a player from a guild (leave or kick).
     */
    public boolean leaveGuild(int guildID, long playerAuth, boolean isKick, long kickerAuth) {
        GuildData guild = guilds.get(guildID);
        if (guild == null) return false;

        // Check if player is member
        GuildRank currentRank = guild.getMemberRank(playerAuth);
        if (currentRank == null) return false;

        // Leaders cannot leave without transferring leadership first
        if (currentRank == GuildRank.LEADER && guild.getMemberCount() > 1) {
            // Auto-transfer to top contributor
            long topContributor = guild.getTopContributor();
            if (topContributor != 0 && topContributor != playerAuth) {
                guild.transferLeadership(topContributor);
                guild.addAuditEntry(GuildAuditEntry.Action.LEADERSHIP_TRANSFERRED, playerAuth,
                        "Leadership auto-transferred to top contributor");
            } else {
                return false; // Can't leave as sole leader with members
            }
        }

        // Remove from guild
        guild.removeMember(playerAuth);

        // Update player data
        PlayerGuildData playerGD = playerData.get(playerAuth);
        if (playerGD != null) {
            playerGD.removeMembership(guildID);
        }

        if (isKick) {
            guild.addAuditEntry(GuildAuditEntry.Action.MEMBER_KICKED, kickerAuth,
                    "Kicked player " + playerAuth);
        } else {
            guild.addAuditEntry(GuildAuditEntry.Action.MEMBER_LEFT, playerAuth, "Left the guild");
        }

        // If last member left, disband
        if (guild.getMemberCount() == 0) {
            guildNameIndex.remove(guild.getName().toLowerCase());
            guilds.remove(guildID);
            ModLogger.debug("Guild '" + guild.getName() + "' auto-disbanded (no members)");
        }

        return true;
    }

    /**
     * Promote or demote a member.
     */
    public boolean setMemberRank(int guildID, long targetAuth, GuildRank newRank, long requestingAuth) {
        GuildData guild = guilds.get(guildID);
        if (guild == null) return false;

        // Check permission
        if (!guild.hasPermission(requestingAuth, PermissionType.PROMOTE_DEMOTE)) {
            return false;
        }

        // Can't change own rank
        if (targetAuth == requestingAuth) return false;

        // Check target is member
        GuildRank currentRank = guild.getMemberRank(targetAuth);
        if (currentRank == null) return false;

        // Can't promote to Leader directly (use transferLeadership)
        if (newRank == GuildRank.LEADER) return false;

        // Update guild
        guild.setMemberRank(targetAuth, newRank);

        // Update player data
        PlayerGuildData playerGD = playerData.get(targetAuth);
        if (playerGD != null) {
            playerGD.updateRank(guildID, newRank);
        }

        GuildAuditEntry.Action action = newRank.level > currentRank.level
                ? GuildAuditEntry.Action.MEMBER_PROMOTED
                : GuildAuditEntry.Action.MEMBER_DEMOTED;
        guild.addAuditEntry(action, requestingAuth,
                "Changed " + targetAuth + " from " + currentRank.getDisplayName() + " to " + newRank.getDisplayName());

        return true;
    }

    /**
     * Transfer guild leadership.
     */
    public boolean transferLeadership(int guildID, long newLeaderAuth, long currentLeaderAuth) {
        GuildData guild = guilds.get(guildID);
        if (guild == null) return false;

        // Verify current leader
        if (guild.getLeaderAuth() != currentLeaderAuth) return false;

        // Check new leader is member and can become leader
        if (!guild.isMember(newLeaderAuth)) return false;

        PlayerGuildData newLeaderData = playerData.get(newLeaderAuth);
        if (newLeaderData != null && !newLeaderData.canBecomeLeader()) {
            return false; // Already leader of another guild
        }

        // Transfer in guild data
        guild.transferLeadership(newLeaderAuth);

        // Update player data
        PlayerGuildData oldLeaderData = playerData.get(currentLeaderAuth);
        if (oldLeaderData != null) {
            oldLeaderData.updateRank(guildID, GuildRank.OFFICER);
        }
        if (newLeaderData != null) {
            newLeaderData.updateRank(guildID, GuildRank.LEADER);
        }

        guild.addAuditEntry(GuildAuditEntry.Action.LEADERSHIP_TRANSFERRED, currentLeaderAuth,
                "Transferred leadership to " + newLeaderAuth);

        return true;
    }

    // === Invitations ===

    public boolean sendInvitation(int guildID, long targetAuth, long senderAuth) {
        GuildData guild = guilds.get(guildID);
        if (guild == null) return false;

        // Check permission
        if (!guild.hasPermission(senderAuth, PermissionType.INVITE_MEMBER)) {
            return false;
        }

        // Check not already member
        if (guild.isMember(targetAuth)) return false;

        // Add invitation to target's player data
        PlayerGuildData targetData = getOrCreatePlayerData(targetAuth);
        targetData.addInvitation(guildID);

        return true;
    }

    public boolean acceptInvitation(int guildID, long playerAuth) {
        PlayerGuildData playerGD = playerData.get(playerAuth);
        if (playerGD == null || !playerGD.hasInvitation(guildID)) {
            return false;
        }

        return joinGuild(guildID, playerAuth, GuildRank.RECRUIT);
    }

    public void declineInvitation(int guildID, long playerAuth) {
        PlayerGuildData playerGD = playerData.get(playerAuth);
        if (playerGD != null) {
            playerGD.removeInvitation(guildID);
        }
    }

    // === Queries ===

    public GuildData getGuild(int guildID) {
        return guilds.get(guildID);
    }

    public GuildData getGuildByName(String name) {
        Integer guildID = guildNameIndex.get(name.toLowerCase());
        return guildID != null ? guilds.get(guildID) : null;
    }

    public PlayerGuildData getPlayerData(long playerAuth) {
        return playerData.get(playerAuth);
    }

    /**
     * Alias for getOrCreatePlayerData for code compatibility.
     */
    public PlayerGuildData getPlayerGuildData(long playerAuth) {
        return getOrCreatePlayerData(playerAuth);
    }

    public PlayerGuildData getOrCreatePlayerData(long playerAuth) {
        return playerData.computeIfAbsent(playerAuth, PlayerGuildData::new);
    }

    public Collection<GuildData> getAllGuilds() {
        return Collections.unmodifiableCollection(guilds.values());
    }

    public List<GuildData> getPublicGuilds() {
        return guilds.values().stream()
                .filter(GuildData::isPublic)
                .collect(Collectors.toList());
    }

    /**
     * Get the set of guild IDs that have pending invitations for a player.
     */
    public Set<Integer> getPendingInvitations(long playerAuth) {
        PlayerGuildData pgd = playerData.get(playerAuth);
        if (pgd == null) {
            return Collections.emptySet();
        }
        return pgd.getPendingInviteGuildIDs();
    }

    public boolean isGuildNameTaken(String name) {
        return guildNameIndex.containsKey(name.toLowerCase());
    }

    public boolean isValidGuildName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        if (trimmed.length() < 3 || trimmed.length() > 32) return false;
        // Allow alphanumeric, spaces, and common punctuation
        return trimmed.matches("^[a-zA-Z0-9 '_-]+$");
    }

    // === Research Tick ===

    /**
     * Called periodically to process scientist research automation.
     */
    public void tickResearch() {
        long now = System.currentTimeMillis();
        if (now - lastResearchTick < RESEARCH_TICK_INTERVAL) {
            return;
        }
        lastResearchTick = now;

        for (GuildData guild : guilds.values()) {
            processGuildResearchTick(guild);
        }
    }

    private void processGuildResearchTick(GuildData guild) {
        List<String> queue = guild.getResearchQueue();
        if (queue.isEmpty()) return;

        // TODO: Check if guild has scientist in settlement
        // For now, process if queue has items

        String currentNodeID = queue.get(0);
        int pullAmount = guild.getScientistPullRate();

        // Withdraw from treasury (respecting minimum threshold)
        long withdrawn = guild.withdrawForResearch(pullAmount);
        if (withdrawn <= 0) return;

        // Add progress to research
        guild.addResearchProgress(currentNodeID, (int) withdrawn);

        // TODO: Check if research is complete (need ResearchTree definition)
    }

    // === Persistence ===

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save); // CRITICAL: Save the stringID so WorldData can load!
        ModLogger.info("GuildManager.addSaveData called - saving %d guilds, %d players", guilds.size(), playerData.size());
        save.addInt("version", SAVE_VERSION);
        save.addInt("nextGuildID", nextGuildID.get());
        save.addLong("lastResearchTick", lastResearchTick);
        save.addInt("nextNotificationID", nextNotificationID.get());

        // Save all guilds
        SaveData guildsSave = new SaveData("guilds");
        for (GuildData guild : guilds.values()) {
            SaveData guildSave = new SaveData("guild");
            guild.addSaveData(guildSave);
            guildsSave.addSaveData(guildSave);
        }
        save.addSaveData(guildsSave);

        // Save player data
        SaveData playersSave = new SaveData("players");
        for (PlayerGuildData playerGD : playerData.values()) {
            SaveData playerSave = new SaveData("player");
            playerGD.addSaveData(playerSave);
            playersSave.addSaveData(playerSave);
        }
        save.addSaveData(playersSave);
        
        // Save notifications
        SaveData notifsSave = new SaveData("notifications");
        for (Map.Entry<Long, List<medievalsim.guilds.notifications.GuildNotification>> entry : playerNotifications.entrySet()) {
            for (medievalsim.guilds.notifications.GuildNotification notif : entry.getValue()) {
                // Skip expired notifications
                if (notif.isExpired()) continue;
                
                SaveData notifSave = new SaveData("notification");
                notifSave.addLong("playerAuth", entry.getKey());
                notifSave.addLong("notificationID", notif.getNotificationID());
                notifSave.addInt("type", notif.getType().ordinal());
                notifSave.addSafeString("title", notif.getTitle());
                notifSave.addSafeString("message", notif.getMessage());
                notifSave.addLong("timestamp", notif.getTimestamp());
                notifSave.addInt("guildID", notif.getGuildID());
                notifSave.addBoolean("read", notif.isRead());
                notifSave.addSafeString("actionData", notif.getActionData() != null ? notif.getActionData() : "");
                notifSave.addLong("ttlMillis", notif.getTtlMillis());
                notifsSave.addSaveData(notifSave);
            }
        }
        save.addSaveData(notifsSave);
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save); // Call parent first
        ModLogger.info("GuildManager.applyLoadData called");
        int version = save.getInt("version", 1);
        nextGuildID.set(save.getInt("nextGuildID", 1));
        lastResearchTick = save.getLong("lastResearchTick", 0);
        nextNotificationID.set(save.getInt("nextNotificationID", 1));

        // Load guilds - use getLoadData() instead of getLoadDataArray()
        guilds.clear();
        guildNameIndex.clear();
        LoadData guildsSave = save.getFirstLoadDataByName("guilds");
        if (guildsSave != null) {
            for (LoadData guildSave : guildsSave.getLoadData()) {
                GuildData guild = new GuildData(guildSave);
                guilds.put(guild.getGuildID(), guild);
                guildNameIndex.put(guild.getName().toLowerCase(), guild.getGuildID());
            }
        }
        ModLogger.info("GuildManager loaded %d guilds from save data", guilds.size());

        // Load player data - use getLoadData() instead of getLoadDataArray()
        playerData.clear();
        LoadData playersSave = save.getFirstLoadDataByName("players");
        if (playersSave != null) {
            for (LoadData playerSave : playersSave.getLoadData()) {
                PlayerGuildData playerGD = new PlayerGuildData(playerSave);
                playerData.put(playerGD.getPlayerAuth(), playerGD);
            }
        }
        
        // Load notifications
        playerNotifications.clear();
        LoadData notifsSave = save.getFirstLoadDataByName("notifications");
        if (notifsSave != null) {
            for (LoadData notifSave : notifsSave.getLoadData()) {
                long playerAuth = notifSave.getLong("playerAuth", 0);
                long notificationID = notifSave.getLong("notificationID", 0);
                int typeOrdinal = notifSave.getInt("type", 0);
                String title = notifSave.getSafeString("title", "");
                String message = notifSave.getSafeString("message", "");
                long timestamp = notifSave.getLong("timestamp", System.currentTimeMillis());
                int guildID = notifSave.getInt("guildID", -1);
                boolean read = notifSave.getBoolean("read", false);
                String actionData = notifSave.getSafeString("actionData", "");
                long ttlMillis = notifSave.getLong("ttlMillis", 0);
                
                medievalsim.guilds.notifications.GuildNotification.NotificationType type = 
                    medievalsim.guilds.notifications.GuildNotification.NotificationType.values()[
                        Math.min(typeOrdinal, medievalsim.guilds.notifications.GuildNotification.NotificationType.values().length - 1)];
                
                // Use constructor with timestamp to preserve original creation time
                medievalsim.guilds.notifications.GuildNotification notif = 
                    new medievalsim.guilds.notifications.GuildNotification(
                        notificationID, type, title, message, guildID, 
                        actionData.isEmpty() ? null : actionData, ttlMillis, timestamp);
                
                // Skip expired notifications on load
                if (notif.isExpired()) continue;
                
                if (read) notif.markRead();
                
                playerNotifications.computeIfAbsent(playerAuth, k -> new ArrayList<>()).add(notif);
            }
        }

        ModLogger.debug("Loaded " + guilds.size() + " guilds, " + playerData.size() + " player records, and " + 
            playerNotifications.values().stream().mapToInt(List::size).sum() + " notifications");
    }

    // === Utility ===

    /**
     * Get player's primary guild (for convenience).
     */
    public GuildData getPlayerPrimaryGuild(long playerAuth) {
        PlayerGuildData playerGD = playerData.get(playerAuth);
        if (playerGD == null || playerGD.getPrimaryGuildID() < 0) return null;
        return guilds.get(playerGD.getPrimaryGuildID());
    }

    /**
     * Check if player has permission in any of their guilds.
     */
    public boolean hasPermissionInAnyGuild(long playerAuth, PermissionType permission) {
        PlayerGuildData playerGD = playerData.get(playerAuth);
        if (playerGD == null) return false;

        for (int guildID : playerGD.getGuildIDs()) {
            GuildData guild = guilds.get(guildID);
            if (guild != null && guild.hasPermission(playerAuth, permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collect tax from a Grand Exchange transaction.
     */
    public void collectTax(long playerAuth, long transactionAmount) {
        PlayerGuildData playerGD = playerData.get(playerAuth);
        if (playerGD == null) return;

        // Collect tax for each guild the player is in (additive)
        for (int guildID : playerGD.getGuildIDs()) {
            GuildData guild = guilds.get(guildID);
            if (guild != null && guild.getTaxRate() > 0) {
                long tax = (long) (transactionAmount * guild.getTaxRate());
                if (tax > 0) {
                    guild.depositTreasury(tax, 0); // 0 = system deposit
                    guild.addAuditEntry(GuildAuditEntry.Action.TAX_COLLECTED, playerAuth,
                            tax + " gold tax from transaction");
                }
            }
        }
    }
    
    // === Notification System ===
    // Per docs: players have an inbox of guild notifications (invites, notices, etc.)
    
    // Player notifications stored by auth
    private final Map<Long, List<medievalsim.guilds.notifications.GuildNotification>> playerNotifications = new ConcurrentHashMap<>();
    private final AtomicInteger nextNotificationID = new AtomicInteger(1);
    
    /**
     * Get all notifications for a player.
     */
    public List<medievalsim.guilds.notifications.GuildNotification> getPlayerNotifications(long playerAuth) {
        List<medievalsim.guilds.notifications.GuildNotification> notifications = playerNotifications.get(playerAuth);
        if (notifications == null) {
            return Collections.emptyList();
        }
        // Remove expired notifications and return
        notifications.removeIf(medievalsim.guilds.notifications.GuildNotification::isExpired);
        return new ArrayList<>(notifications);
    }
    
    /**
     * Add a notification for a player.
     */
    public void addPlayerNotification(long playerAuth, medievalsim.guilds.notifications.GuildNotification notification) {
        playerNotifications.computeIfAbsent(playerAuth, k -> new ArrayList<>()).add(notification);
        ModLogger.debug("Added notification %d for player %d: %s", 
            notification.getNotificationID(), playerAuth, notification.getTitle());
    }
    
    /**
     * Create and add a new notification for a player.
     */
    public medievalsim.guilds.notifications.GuildNotification createNotification(
            long playerAuth, 
            medievalsim.guilds.notifications.GuildNotification.NotificationType type,
            String title, String message, int guildID) {
        medievalsim.guilds.notifications.GuildNotification notif = 
            new medievalsim.guilds.notifications.GuildNotification(
                nextNotificationID.getAndIncrement(), type, title, message, guildID);
        addPlayerNotification(playerAuth, notif);
        return notif;
    }
    
    /**
     * Clear a specific notification for a player.
     * @return true if notification was found and cleared.
     */
    public boolean clearPlayerNotification(long playerAuth, long notificationID) {
        List<medievalsim.guilds.notifications.GuildNotification> notifications = playerNotifications.get(playerAuth);
        if (notifications == null) return false;
        return notifications.removeIf(n -> n.getNotificationID() == notificationID);
    }
    
    /**
     * Clear all notifications for a player.
     * @return number of notifications cleared.
     */
    public int clearAllPlayerNotifications(long playerAuth) {
        List<medievalsim.guilds.notifications.GuildNotification> notifications = playerNotifications.remove(playerAuth);
        return notifications != null ? notifications.size() : 0;
    }
    
    /**
     * Get unread notification count for a player.
     */
    public int getUnreadNotificationCount(long playerAuth) {
        List<medievalsim.guilds.notifications.GuildNotification> notifications = playerNotifications.get(playerAuth);
        if (notifications == null) return 0;
        return (int) notifications.stream().filter(n -> !n.isRead() && !n.isExpired()).count();
    }
}
