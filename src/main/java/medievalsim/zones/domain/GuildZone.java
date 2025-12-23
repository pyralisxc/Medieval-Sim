/*
 * Guild Zone Implementation for Medieval Sim Mod
 * Provides territory protection and management for guild-owned areas
 * Integrated into the AdminZone system alongside Protected and PvP zones
 */
package medievalsim.zones.domain;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.GuildRank;
import medievalsim.guilds.PermissionType;
import medievalsim.util.ModLogger;
import necesse.engine.Settings;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.ServerClient;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * Guild Zone - territory claimed by a guild.
 * Permissions are determined by the guild's permission configuration rather than
 * individual zone settings. This keeps territory management simple while leveraging
 * the full guild permission system.
 * 
 * Integrated into AdminZonesLevelData alongside ProtectedZone and PvPZone.
 */
public class GuildZone extends AdminZone {
    public static final String TYPE_ID = "guild";

    // Guild association
    private int guildID = -1;
    private String guildName = "";  // Cached for display when guild data not available

    // Zone-specific overrides (if needed for special plots)
    private boolean allowNonMembers = false;  // Allow non-guild-members to enter
    private boolean isPurchasable = false;    // Can be purchased by guild (for expansion)
    private long purchaseCost = 0;            // Cost to purchase if purchasable

    // Ally access
    private Set<Integer> allyGuildIDs = new HashSet<>();  // Allied guilds with access

    public GuildZone() {
        super();
    }

    public GuildZone(int uniqueID, String name, long creatorAuth, int colorHue) {
        super(uniqueID, name, creatorAuth, colorHue);
    }

    public GuildZone(int uniqueID, String name, long creatorAuth, int colorHue, int guildID) {
        super(uniqueID, name, creatorAuth, colorHue);
        this.guildID = guildID;
    }

    @Override
    public String getTypeID() {
        return TYPE_ID;
    }

    // === Guild Association ===

    public int getGuildID() {
        return guildID;
    }

    public void setGuildID(int guildID) {
        this.guildID = guildID;
    }

    public String getGuildName() {
        return guildName;
    }

    public void setGuildName(String guildName) {
        this.guildName = guildName;
    }

    /**
     * Get the number of tiles in this zone.
     */
    public int getTileCount() {
        return zoning != null ? zoning.size() : 0;
    }

    // === Permission Checks ===

    /**
     * Check if a client is a member of the owning guild.
     */
    public boolean isGuildMember(ServerClient client) {
        if (client == null || guildID < 0) return false;

        GuildManager gm = GuildManager.get(client.getServer().world);
        if (gm == null) return false;

        return gm.getPlayerGuildData(client.authentication).isMemberOf(guildID);
    }

    /**
     * Check if a client is a member of an allied guild.
     */
    public boolean isAllyMember(ServerClient client) {
        if (client == null || allyGuildIDs.isEmpty()) return false;

        GuildManager gm = GuildManager.get(client.getServer().world);
        if (gm == null) return false;

        for (int allyID : allyGuildIDs) {
            if (gm.getPlayerGuildData(client.authentication).isMemberOf(allyID)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the client's rank within the owning guild.
     * Returns null if not a member.
     */
    public GuildRank getClientRank(ServerClient client) {
        if (client == null || guildID < 0) return null;

        GuildManager gm = GuildManager.get(client.getServer().world);
        if (gm == null) return null;

        GuildData guild = gm.getGuild(guildID);
        if (guild == null) return null;

        return guild.getMemberRank(client.authentication);
    }

    /**
     * Check if client has a specific permission within this zone.
     * Uses the guild's permission system.
     */
    public boolean hasPermission(ServerClient client, PermissionType permission) {
        if (client == null || guildID < 0) return false;

        // World owner always has full access
        if (isWorldOwner(client, client.getLevel())) {
            return true;
        }

        GuildManager gm = GuildManager.get(client.getServer().world);
        if (gm == null) return false;

        GuildData guild = gm.getGuild(guildID);
        if (guild == null) return false;

        GuildRank rank = guild.getMemberRank(client.authentication);
        if (rank == null) {
            // Not a member - check if allies have build permission
            if (isAllyMember(client)) {
                // Allies get limited permissions (configurable per-guild in future)
                return permission == PermissionType.ACCESS_ZONE;
            }
            return false;
        }

        return guild.hasPermission(rank, permission);
    }

    /**
     * Check if the client is the world owner.
     */
    public boolean isWorldOwner(ServerClient client, Level level) {
        if (client == null || level == null || !level.isServer()) {
            return false;
        }
        return Settings.serverOwnerAuth != -1L && client.authentication == Settings.serverOwnerAuth;
    }

    // === Zone Access Checks (mirroring ProtectedZone pattern) ===

    /**
     * Can the client enter this zone?
     */
    public boolean canClientEnter(ServerClient client) {
        if (client == null) return false;
        if (allowNonMembers) return true;
        if (isWorldOwner(client, client.getLevel())) return true;
        if (isGuildMember(client)) return true;
        if (isAllyMember(client)) return true;
        return false;
    }

    /**
     * Can the client modify (build/break) in this zone?
     * Used by ProtectionFacade for consistent permission checks.
     */
    public boolean canClientModify(ServerClient client, Level level) {
        if (client == null) return false;
        if (isWorldOwner(client, level)) return true;
        return hasPermission(client, PermissionType.BUILD);
    }

    /**
     * Can the client break blocks/objects in this zone?
     */
    public boolean canClientBreak(ServerClient client, Level level) {
        return canClientModify(client, level);
    }

    /**
     * Can the client place blocks/objects in this zone?
     */
    public boolean canClientPlace(ServerClient client, Level level) {
        return canClientModify(client, level);
    }

    /**
     * Can the client interact with objects in this zone?
     */
    public boolean canClientInteract(ServerClient client, Level level) {
        if (client == null) return false;
        if (isWorldOwner(client, level)) return true;
        return hasPermission(client, PermissionType.INTERACT);
    }

    /**
     * Can the client access containers in this zone?
     */
    public boolean canClientAccessContainers(ServerClient client, Level level) {
        if (client == null) return false;
        if (isWorldOwner(client, level)) return true;
        return hasPermission(client, PermissionType.ACCESS_STORAGE);
    }

    /**
     * Full access check for elevated users (officers+).
     */
    public boolean hasElevatedAccess(ServerClient client, Level level) {
        if (client == null) return false;
        if (isWorldOwner(client, level)) return true;

        GuildRank rank = getClientRank(client);
        if (rank == null) return false;

        return rank.hasRank(GuildRank.OFFICER);
    }

    // === Ally Management ===

    public void addAllyGuild(int allyGuildID) {
        allyGuildIDs.add(allyGuildID);
    }

    public void removeAllyGuild(int allyGuildID) {
        allyGuildIDs.remove(allyGuildID);
    }

    public Set<Integer> getAllyGuildIDs() {
        return new HashSet<>(allyGuildIDs);
    }

    public void setAllyGuildIDs(Set<Integer> allies) {
        this.allyGuildIDs = new HashSet<>(allies);
    }

    // === Purchasable Plot Settings ===

    public boolean isPurchasable() {
        return isPurchasable;
    }

    public void setPurchasable(boolean purchasable, long cost) {
        this.isPurchasable = purchasable;
        this.purchaseCost = cost;
    }

    public long getPurchaseCost() {
        return purchaseCost;
    }

    public boolean isAllowNonMembers() {
        return allowNonMembers;
    }

    public void setAllowNonMembers(boolean allow) {
        this.allowNonMembers = allow;
    }

    // === Persistence ===

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);
        save.addInt("guildID", guildID);
        save.addUnsafeString("guildName", guildName);
        save.addBoolean("allowNonMembers", allowNonMembers);
        save.addBoolean("isPurchasable", isPurchasable);
        save.addLong("purchaseCost", purchaseCost);

        // Save ally IDs
        int[] allyArray = allyGuildIDs.stream().mapToInt(Integer::intValue).toArray();
        save.addIntArray("allyGuildIDs", allyArray);
    }

    @Override
    public void applyLoadData(LoadData load) {
        super.applyLoadData(load);
        this.guildID = load.getInt("guildID", -1);
        this.guildName = load.getUnsafeString("guildName", "");
        this.allowNonMembers = load.getBoolean("allowNonMembers", false);
        this.isPurchasable = load.getBoolean("isPurchasable", false);
        this.purchaseCost = load.getLong("purchaseCost", 0);

        // Load ally IDs
        this.allyGuildIDs.clear();
        int[] allyArray = load.getIntArray("allyGuildIDs");
        if (allyArray != null) {
            for (int id : allyArray) {
                allyGuildIDs.add(id);
            }
        }
    }

    @Override
    public void writePacket(PacketWriter writer) {
        super.writePacket(writer);
        writer.putNextInt(guildID);
        writer.putNextString(guildName);
        writer.putNextBoolean(allowNonMembers);
        writer.putNextBoolean(isPurchasable);
        writer.putNextLong(purchaseCost);

        // Write ally IDs
        writer.putNextInt(allyGuildIDs.size());
        for (int allyID : allyGuildIDs) {
            writer.putNextInt(allyID);
        }
    }

    @Override
    public void readPacket(PacketReader reader) {
        super.readPacket(reader);
        this.guildID = reader.getNextInt();
        this.guildName = reader.getNextString();
        this.allowNonMembers = reader.getNextBoolean();
        this.isPurchasable = reader.getNextBoolean();
        this.purchaseCost = reader.getNextLong();

        // Read ally IDs
        this.allyGuildIDs.clear();
        int allyCount = reader.getNextInt();
        for (int i = 0; i < allyCount; i++) {
            allyGuildIDs.add(reader.getNextInt());
        }
    }

    // === Utility ===

    /**
     * Sync guild name from guild data (call when guild is renamed).
     */
    public void syncGuildName(GuildManager gm) {
        if (guildID >= 0 && gm != null) {
            GuildData guild = gm.getGuild(guildID);
            if (guild != null) {
                this.guildName = guild.getName();
            }
        }
    }

    /**
     * Check if this zone should be removed (orphaned guild).
     */
    @Override
    public boolean shouldRemove() {
        if (super.shouldRemove()) return true;
        // Zone becomes orphaned if guild is disbanded
        // This will be handled by GuildManager when a guild is disbanded
        return false;
    }

    @Override
    public String toString() {
        return "GuildZone{id=" + uniqueID + ", name='" + name + "', guild=" + guildID + 
               " (" + guildName + "), tiles=" + getTileCount() + "}";
    }
}
