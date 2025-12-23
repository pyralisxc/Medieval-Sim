package medievalsim.guilds;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

import java.util.*;

/**
 * Tracks a player's guild memberships across all guilds.
 * Stored per-player in world save.
 */
public class PlayerGuildData {
    private final long playerAuth;

    // Guild memberships: guildID -> rank (mirrors GuildData.members for fast lookup)
    private final Map<Integer, GuildRank> memberships = new HashMap<>();

    // Primary guild for HUD/teleport (player-selected)
    private int primaryGuildID = -1;

    // Pending invitations: guildID -> timestamp
    private final Map<Integer, Long> pendingInvites = new HashMap<>();
    private static final long INVITE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    // Daily withdraw tracking for officers
    private final Map<Integer, DailyWithdrawTracker> withdrawTrackers = new HashMap<>();

    public PlayerGuildData(long playerAuth) {
        this.playerAuth = playerAuth;
    }

    public PlayerGuildData(LoadData save) {
        this.playerAuth = save.getLong("playerAuth", 0);
        this.primaryGuildID = save.getInt("primaryGuildID", -1);

        // Load memberships - use getLoadData() instead of getLoadDataArray()
        LoadData membershipsData = save.getFirstLoadDataByName("memberships");
        if (membershipsData != null) {
            for (LoadData entry : membershipsData.getLoadData()) {
                int guildID = entry.getInt("guildID", -1);
                int rankLevel = entry.getInt("rank", 1);
                if (guildID >= 0) {
                    memberships.put(guildID, GuildRank.fromLevel(rankLevel));
                }
            }
        }

        // Load pending invites - use getLoadData() instead of getLoadDataArray()
        LoadData invitesData = save.getFirstLoadDataByName("pendingInvites");
        if (invitesData != null) {
            for (LoadData entry : invitesData.getLoadData()) {
                int guildID = entry.getInt("guildID", -1);
                long timestamp = entry.getLong("timestamp", 0);
                if (guildID >= 0 && !isInviteExpired(timestamp)) {
                    pendingInvites.put(guildID, timestamp);
                }
            }
        }
    }

    public void addSaveData(SaveData save) {
        save.addLong("playerAuth", playerAuth);
        save.addInt("primaryGuildID", primaryGuildID);

        // Save memberships
        SaveData membershipsSave = new SaveData("memberships");
        for (Map.Entry<Integer, GuildRank> entry : memberships.entrySet()) {
            SaveData entrySave = new SaveData("entry");
            entrySave.addInt("guildID", entry.getKey());
            entrySave.addInt("rank", entry.getValue().level);
            membershipsSave.addSaveData(entrySave);
        }
        save.addSaveData(membershipsSave);

        // Save pending invites (filter expired)
        SaveData invitesSave = new SaveData("pendingInvites");
        for (Map.Entry<Integer, Long> entry : pendingInvites.entrySet()) {
            if (!isInviteExpired(entry.getValue())) {
                SaveData entrySave = new SaveData("entry");
                entrySave.addInt("guildID", entry.getKey());
                entrySave.addLong("timestamp", entry.getValue());
                invitesSave.addSaveData(entrySave);
            }
        }
        save.addSaveData(invitesSave);
    }

    // === Getters ===

    public long getPlayerAuth() { return playerAuth; }
    public int getPrimaryGuildID() { return primaryGuildID; }
    public int getGuildCount() { return memberships.size(); }

    public Map<Integer, GuildRank> getMemberships() {
        return Collections.unmodifiableMap(memberships);
    }

    public GuildRank getRankInGuild(int guildID) {
        return memberships.get(guildID);
    }

    public boolean isInGuild(int guildID) {
        return memberships.containsKey(guildID);
    }

    /**
     * Alias for isInGuild for code compatibility.
     */
    public boolean isMemberOf(int guildID) {
        return isInGuild(guildID);
    }

    public boolean isInAnyGuild() {
        return !memberships.isEmpty();
    }

    public Set<Integer> getGuildIDs() {
        return Collections.unmodifiableSet(memberships.keySet());
    }

    // === Membership Management ===

    public boolean canJoinMoreGuilds(int maxGuilds) {
        return memberships.size() < maxGuilds;
    }

    public boolean canBecomeLeader() {
        // Player can only be leader of one guild at a time
        return memberships.values().stream()
                .noneMatch(rank -> rank == GuildRank.LEADER);
    }

    public int getLeaderGuildID() {
        for (Map.Entry<Integer, GuildRank> entry : memberships.entrySet()) {
            if (entry.getValue() == GuildRank.LEADER) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * Add guild membership (called when joining a guild).
     */
    public void addMembership(int guildID, GuildRank rank) {
        memberships.put(guildID, rank);
        pendingInvites.remove(guildID);

        // Set as primary if first guild
        if (primaryGuildID < 0) {
            primaryGuildID = guildID;
        }
    }

    /**
     * Remove guild membership (called when leaving/kicked).
     */
    public void removeMembership(int guildID) {
        memberships.remove(guildID);
        withdrawTrackers.remove(guildID);

        // Update primary if needed
        if (primaryGuildID == guildID) {
            primaryGuildID = memberships.isEmpty() ? -1 : memberships.keySet().iterator().next();
        }
    }

    /**
     * Update rank (called when promoted/demoted).
     */
    public void updateRank(int guildID, GuildRank newRank) {
        if (memberships.containsKey(guildID)) {
            memberships.put(guildID, newRank);
        }
    }

    public void setPrimaryGuild(int guildID) {
        if (memberships.containsKey(guildID)) {
            this.primaryGuildID = guildID;
        }
    }

    // === Invitations ===

    public void addInvitation(int guildID) {
        pendingInvites.put(guildID, System.currentTimeMillis());
    }

    public void removeInvitation(int guildID) {
        pendingInvites.remove(guildID);
    }

    public boolean hasInvitation(int guildID) {
        Long timestamp = pendingInvites.get(guildID);
        if (timestamp == null) return false;
        if (isInviteExpired(timestamp)) {
            pendingInvites.remove(guildID);
            return false;
        }
        return true;
    }

    public Set<Integer> getPendingInviteGuildIDs() {
        // Clean expired invites
        pendingInvites.entrySet().removeIf(e -> isInviteExpired(e.getValue()));
        return Collections.unmodifiableSet(pendingInvites.keySet());
    }

    private boolean isInviteExpired(long timestamp) {
        return System.currentTimeMillis() - timestamp > INVITE_EXPIRY_MS;
    }

    // === Withdraw Tracking ===

    public DailyWithdrawTracker getWithdrawTracker(int guildID) {
        return withdrawTrackers.computeIfAbsent(guildID, id -> new DailyWithdrawTracker());
    }

    /**
     * Tracks daily treasury withdrawals for officers.
     */
    public static class DailyWithdrawTracker {
        private long todayTotal = 0;
        private int lastResetDay = -1;

        public long getTodayTotal() {
            checkDayReset();
            return todayTotal;
        }

        public void addWithdrawal(long amount) {
            checkDayReset();
            todayTotal += amount;
        }

        public boolean canWithdraw(long amount, long dailyLimit) {
            if (dailyLimit <= 0) return true; // 0 = unlimited
            checkDayReset();
            return todayTotal + amount <= dailyLimit;
        }

        private void checkDayReset() {
            int currentDay = (int) (System.currentTimeMillis() / (24 * 60 * 60 * 1000L));
            if (currentDay != lastResetDay) {
                todayTotal = 0;
                lastResetDay = currentDay;
            }
        }
    }
}
