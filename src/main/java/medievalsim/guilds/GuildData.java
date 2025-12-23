package medievalsim.guilds;

import medievalsim.guilds.bank.GuildBank;
import medievalsim.guilds.research.GuildResearchManager;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core guild data structure containing all guild state.
 * Persisted to world save file.
 */
public class GuildData {
    // Identification
    private final int guildID;
    private String name;
    private String description;
    private boolean isPublic;
    private final long createdTimestamp;
    private long founderAuth;

    // Membership - playerAuth -> rank
    private final Map<Long, GuildRank> members = new ConcurrentHashMap<>();

    // Permissions - type -> minimum rank required
    private final Map<PermissionType, GuildRank> permissions = new EnumMap<>(PermissionType.class);

    // Guild Bank
    private GuildBank bank;

    // Research Manager (lazy initialized)
    private GuildResearchManager researchManager;

    // Treasury
    private long treasuryGold = 0;
    private long treasuryMinimumThreshold = 10000; // Default 10k gold

    // Tax rate (0.0 to 0.15 = 0% to 15%)
    private float taxRate = 0.03f; // Default 3%

    // Research
    private final Map<String, Integer> researchProgress = new HashMap<>(); // nodeID -> progress
    private final List<String> researchQueue = new ArrayList<>(); // Up to 3 priorities
    private int scientistPullRate = 10000; // Resources per hour

    // Symbol design
    private GuildSymbolDesign symbolDesign;
    private int symbolRevision = 0;
    private int colorHue = 0; // Guild's primary color (0-360)

    // Contribution tracking (for leadership transfer)
    private final Map<Long, Long> contributionScores = new ConcurrentHashMap<>();

    // Audit log (last N entries)
    private final List<GuildAuditEntry> auditLog = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_AUDIT_ENTRIES = 100;

    /**
     * Create a new guild.
     */
    public GuildData(int guildID, String name, long founderAuth) {
        this.guildID = guildID;
        this.name = name;
        this.description = "";
        this.isPublic = true;
        this.createdTimestamp = System.currentTimeMillis();
        this.founderAuth = founderAuth;

        // Add founder as leader
        members.put(founderAuth, GuildRank.LEADER);

        // Initialize default permissions
        initDefaultPermissions();

        // Initialize default symbol
        this.symbolDesign = new GuildSymbolDesign();
        
        // Initialize guild bank
        this.bank = new GuildBank(guildID);
        
        // Initialize research manager
        this.researchManager = new GuildResearchManager(guildID);
    }

    /**
     * Load guild from save data.
     */
    public GuildData(LoadData save) {
        this.guildID = save.getInt("guildID");
        // Use getFirstDataByName for strings, with manual null check
        String loadedName = save.getFirstDataByName("name");
        this.name = loadedName != null ? loadedName : "Unnamed Guild";
        String loadedDesc = save.getFirstDataByName("description");
        this.description = loadedDesc != null ? loadedDesc : "";
        this.isPublic = save.getBoolean("isPublic", true);
        this.createdTimestamp = save.getLong("createdTimestamp", System.currentTimeMillis());
        this.founderAuth = save.getLong("founderAuth", 0);

        // Load treasury
        this.treasuryGold = save.getLong("treasuryGold", 0);
        this.treasuryMinimumThreshold = save.getLong("treasuryMinimumThreshold", 10000);
        this.taxRate = save.getFloat("taxRate", 0.03f);

        // Load scientist settings
        this.scientistPullRate = save.getInt("scientistPullRate", 10000);

        // Load symbol
        this.symbolRevision = save.getInt("symbolRevision", 0);
        LoadData symbolData = save.getFirstLoadDataByName("symbol");
        if (symbolData != null) {
            this.symbolDesign = new GuildSymbolDesign(symbolData);
        } else {
            this.symbolDesign = new GuildSymbolDesign();
        }

        // Load members - use getLoadData() instead of getLoadDataArray()
        LoadData membersData = save.getFirstLoadDataByName("members");
        if (membersData != null) {
            for (LoadData memberData : membersData.getLoadData()) {
                long playerAuth = memberData.getLong("auth", 0);
                int rankLevel = memberData.getInt("rank", 1);
                if (playerAuth != 0) {
                    members.put(playerAuth, GuildRank.fromLevel(rankLevel));
                }
            }
        }

        // Load permissions
        initDefaultPermissions();
        LoadData permsData = save.getFirstLoadDataByName("permissions");
        if (permsData != null) {
            for (PermissionType type : PermissionType.values()) {
                int rankLevel = permsData.getInt(type.name(), type.getDefaultMinimumRank().level);
                permissions.put(type, GuildRank.fromLevel(rankLevel));
            }
        }

        // Load research progress - iterate via getLoadData() and use getName() as key
        LoadData researchData = save.getFirstLoadDataByName("researchProgress");
        if (researchData != null) {
            for (LoadData entry : researchData.getLoadData()) {
                String nodeID = entry.getName();
                int progress = entry.getInt("value", 0);
                researchProgress.put(nodeID, progress);
            }
        }

        // Load research queue
        LoadData queueData = save.getFirstLoadDataByName("researchQueue");
        if (queueData != null) {
            int count = queueData.getInt("count", 0);
            for (int i = 0; i < count; i++) {
                String nodeID = queueData.getFirstDataByName("node" + i);
                if (nodeID != null) {
                    researchQueue.add(nodeID);
                }
            }
        }

        // Load contribution scores - use getLoadData() instead of getLoadDataArray()
        LoadData contribData = save.getFirstLoadDataByName("contributions");
        if (contribData != null) {
            for (LoadData entry : contribData.getLoadData()) {
                long auth = entry.getLong("auth", 0);
                long score = entry.getLong("score", 0);
                if (auth != 0) {
                    contributionScores.put(auth, score);
                }
            }
        }
        
        // Load guild bank
        this.bank = new GuildBank(guildID);
        LoadData bankData = save.getFirstLoadDataByName("bank");
        if (bankData != null) {
            this.bank.applyLoadData(bankData);
        }
    }

    /**
     * Save guild to save data.
     */
    public void addSaveData(SaveData save) {
        save.addInt("guildID", guildID);
        save.addSafeString("name", name);
        save.addSafeString("description", description);
        save.addBoolean("isPublic", isPublic);
        save.addLong("createdTimestamp", createdTimestamp);
        save.addLong("founderAuth", founderAuth);

        // Treasury
        save.addLong("treasuryGold", treasuryGold);
        save.addLong("treasuryMinimumThreshold", treasuryMinimumThreshold);
        save.addFloat("taxRate", taxRate);

        // Scientist
        save.addInt("scientistPullRate", scientistPullRate);

        // Symbol
        save.addInt("symbolRevision", symbolRevision);
        SaveData symbolSave = new SaveData("symbol");
        symbolDesign.addSaveData(symbolSave);
        save.addSaveData(symbolSave);

        // Members
        SaveData membersSave = new SaveData("members");
        for (Map.Entry<Long, GuildRank> entry : members.entrySet()) {
            SaveData memberSave = new SaveData("member");
            memberSave.addLong("auth", entry.getKey());
            memberSave.addInt("rank", entry.getValue().level);
            membersSave.addSaveData(memberSave);
        }
        save.addSaveData(membersSave);

        // Permissions
        SaveData permsSave = new SaveData("permissions");
        for (Map.Entry<PermissionType, GuildRank> entry : permissions.entrySet()) {
            permsSave.addInt(entry.getKey().name(), entry.getValue().level);
        }
        save.addSaveData(permsSave);

        // Research progress - save as individual entries with value
        SaveData researchSave = new SaveData("researchProgress");
        for (Map.Entry<String, Integer> entry : researchProgress.entrySet()) {
            SaveData nodeSave = new SaveData(entry.getKey());
            nodeSave.addInt("value", entry.getValue());
            researchSave.addSaveData(nodeSave);
        }
        save.addSaveData(researchSave);

        // Research queue
        SaveData queueSave = new SaveData("researchQueue");
        queueSave.addInt("count", researchQueue.size());
        for (int i = 0; i < researchQueue.size(); i++) {
            queueSave.addSafeString("node" + i, researchQueue.get(i));
        }
        save.addSaveData(queueSave);

        // Contributions
        SaveData contribSave = new SaveData("contributions");
        for (Map.Entry<Long, Long> entry : contributionScores.entrySet()) {
            SaveData entrySave = new SaveData("entry");
            entrySave.addLong("auth", entry.getKey());
            entrySave.addLong("score", entry.getValue());
            contribSave.addSaveData(entrySave);
        }
        save.addSaveData(contribSave);
        
        // Guild bank
        if (bank != null) {
            SaveData bankSave = new SaveData("bank");
            bank.addSaveData(bankSave);
            save.addSaveData(bankSave);
        }
    }

    private void initDefaultPermissions() {
        for (PermissionType type : PermissionType.values()) {
            permissions.put(type, type.getDefaultMinimumRank());
        }
    }

    // === Getters ===

    public int getGuildID() { return guildID; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isPublic() { return isPublic; }
    public long getCreatedTimestamp() { return createdTimestamp; }
    public long getFounderAuth() { return founderAuth; }
    public GuildBank getBank() { return bank; }
    public GuildResearchManager getResearchManager() { 
        if (researchManager == null) {
            researchManager = new GuildResearchManager(guildID);
        }
        return researchManager; 
    }
    public long getTreasury() { return treasuryGold; }
    public long getTreasuryGold() { return treasuryGold; }
    public long getTreasuryMinimumThreshold() { return treasuryMinimumThreshold; }
    public float getTaxRate() { return taxRate; }
    public int getScientistPullRate() { return scientistPullRate; }
    public GuildSymbolDesign getSymbolDesign() { return symbolDesign; }
    public void setSymbolDesign(GuildSymbolDesign design) { 
        if (design != null) {
            this.symbolDesign = design;
            this.symbolRevision++;
        }
    }
    public int getSymbolRevision() { return symbolRevision; }
    public int getColorHue() { return colorHue; }
    public int getMemberCount() { return members.size(); }

    // === Setters (with validation) ===

    public void setName(String name) {
        if (name != null && name.length() >= 3 && name.length() <= 32) {
            this.name = name;
        }
    }

    public void setDescription(String description) {
        if (description != null && description.length() <= 256) {
            this.description = description;
        }
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public void setTreasuryMinimumThreshold(long threshold) {
        this.treasuryMinimumThreshold = Math.max(0, threshold);
    }

    public void setTaxRate(float rate) {
        this.taxRate = Math.max(0, Math.min(0.15f, rate)); // 0-15%
    }

    public void setScientistPullRate(int rate) {
        this.scientistPullRate = Math.max(1000, Math.min(10000, rate));
    }

    // === Membership ===

    public Map<Long, GuildRank> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public GuildRank getMemberRank(long playerAuth) {
        return members.get(playerAuth);
    }

    public boolean isMember(long playerAuth) {
        return members.containsKey(playerAuth);
    }

    public boolean addMember(long playerAuth, GuildRank rank) {
        if (!members.containsKey(playerAuth)) {
            members.put(playerAuth, rank);
            contributionScores.put(playerAuth, 0L);
            return true;
        }
        return false;
    }

    public boolean removeMember(long playerAuth) {
        if (members.containsKey(playerAuth)) {
            members.remove(playerAuth);
            return true;
        }
        return false;
    }

    public boolean setMemberRank(long playerAuth, GuildRank newRank) {
        if (members.containsKey(playerAuth)) {
            members.put(playerAuth, newRank);
            return true;
        }
        return false;
    }

    public long getLeaderAuth() {
        for (Map.Entry<Long, GuildRank> entry : members.entrySet()) {
            if (entry.getValue() == GuildRank.LEADER) {
                return entry.getKey();
            }
        }
        return 0;
    }

    public boolean transferLeadership(long newLeaderAuth) {
        if (!members.containsKey(newLeaderAuth)) return false;

        long currentLeader = getLeaderAuth();
        if (currentLeader != 0) {
            members.put(currentLeader, GuildRank.OFFICER);
        }
        members.put(newLeaderAuth, GuildRank.LEADER);
        return true;
    }

    /**
     * Get the member with highest contribution score (for automatic leadership transfer).
     */
    public long getTopContributor() {
        long topAuth = 0;
        long topScore = -1;
        for (Map.Entry<Long, Long> entry : contributionScores.entrySet()) {
            if (entry.getValue() > topScore && members.containsKey(entry.getKey())) {
                topScore = entry.getValue();
                topAuth = entry.getKey();
            }
        }
        return topAuth;
    }

    public void addContribution(long playerAuth, long amount) {
        contributionScores.merge(playerAuth, amount, Long::sum);
    }

    // === Permissions ===

    public GuildRank getPermissionRank(PermissionType type) {
        return permissions.getOrDefault(type, type.getDefaultMinimumRank());
    }

    public boolean setPermissionRank(PermissionType type, GuildRank minRank) {
        if (!type.isCustomizable()) return false;
        permissions.put(type, minRank);
        return true;
    }

    public boolean hasPermission(long playerAuth, PermissionType type) {
        GuildRank playerRank = members.get(playerAuth);
        if (playerRank == null) return false;
        GuildRank required = getPermissionRank(type);
        return playerRank.hasRank(required);
    }
    
    /**
     * Check if a rank has permission (convenience method).
     */
    public boolean hasPermission(GuildRank rank, PermissionType type) {
        if (rank == null) return false;
        GuildRank required = getPermissionRank(type);
        return rank.hasRank(required);
    }

    // === Treasury ===

    public boolean depositTreasury(long amount, long playerAuth) {
        if (amount <= 0) return false;
        treasuryGold += amount;
        addContribution(playerAuth, amount);
        addAuditEntry(GuildAuditEntry.Action.TREASURY_DEPOSIT, playerAuth, amount + " gold deposited");
        return true;
    }

    public boolean withdrawTreasury(long amount, long playerAuth) {
        if (amount <= 0 || treasuryGold < amount) return false;
        treasuryGold -= amount;
        addAuditEntry(GuildAuditEntry.Action.TREASURY_WITHDRAW, playerAuth, amount + " gold withdrawn");
        return true;
    }

    /**
     * Withdraw from treasury (system use, no player auth).
     */
    public boolean withdrawTreasury(long amount) {
        if (amount <= 0 || treasuryGold < amount) return false;
        treasuryGold -= amount;
        addAuditEntry(GuildAuditEntry.Action.TREASURY_WITHDRAW, 0L, amount + " gold withdrawn (system)");
        return true;
    }

    /**
     * Withdraw for scientist automation, respecting minimum threshold.
     * Returns actual amount withdrawn (may be less than requested).
     */
    public long withdrawForResearch(long requestedAmount) {
        long available = treasuryGold - treasuryMinimumThreshold;
        if (available <= 0) return 0;
        long actualWithdraw = Math.min(requestedAmount, available);
        treasuryGold -= actualWithdraw;
        return actualWithdraw;
    }

    // === Research ===

    public Map<String, Integer> getResearchProgress() {
        return Collections.unmodifiableMap(researchProgress);
    }

    public int getResearchProgress(String nodeID) {
        return researchProgress.getOrDefault(nodeID, 0);
    }

    public void addResearchProgress(String nodeID, int amount) {
        researchProgress.merge(nodeID, amount, Integer::sum);
    }

    public List<String> getResearchQueue() {
        return Collections.unmodifiableList(researchQueue);
    }

    public boolean addToResearchQueue(String nodeID) {
        if (researchQueue.size() >= 3 || researchQueue.contains(nodeID)) return false;
        researchQueue.add(nodeID);
        return true;
    }

    public boolean removeFromResearchQueue(String nodeID) {
        return researchQueue.remove(nodeID);
    }

    public void clearResearchQueue() {
        researchQueue.clear();
    }

    // === Crest ===

    public void updateSymbol(GuildSymbolDesign newDesign) {
        this.symbolDesign = newDesign;
        this.symbolRevision++;
    }

    public int getSymbolUID() {
        // Simple deterministic UID based on guildID and revision
        return (guildID * 1000000) + symbolRevision;
    }

    // === Audit Log ===

    public void addAuditEntry(GuildAuditEntry.Action action, long playerAuth, String details) {
        GuildAuditEntry entry = new GuildAuditEntry(action, playerAuth, details);
        auditLog.add(entry);
        while (auditLog.size() > MAX_AUDIT_ENTRIES) {
            auditLog.remove(0);
        }
    }
    
    /**
     * Add pre-constructed audit entry.
     */
    public void addAuditEntry(GuildAuditEntry entry) {
        auditLog.add(entry);
        while (auditLog.size() > MAX_AUDIT_ENTRIES) {
            auditLog.remove(0);
        }
    }

    public List<GuildAuditEntry> getAuditLog() {
        return Collections.unmodifiableList(auditLog);
    }

    // === Disband ===

    /**
     * Distribute treasury evenly to members on disband.
     * Returns map of playerAuth -> gold received.
     */
    public Map<Long, Long> disbandDistributeTreasury() {
        Map<Long, Long> distribution = new HashMap<>();
        if (members.isEmpty() || treasuryGold == 0) {
            return distribution;
        }

        long perMember = treasuryGold / members.size();
        long remainder = treasuryGold % members.size();

        for (Long auth : members.keySet()) {
            distribution.put(auth, perMember);
        }

        // Remainder goes to leader
        long leaderAuth = getLeaderAuth();
        if (leaderAuth != 0 && remainder > 0) {
            distribution.merge(leaderAuth, remainder, Long::sum);
        }

        treasuryGold = 0;
        return distribution;
    }
}
