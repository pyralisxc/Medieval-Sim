/*
 * Guild Research Manager for Medieval Sim Mod
 * Tracks research progress and provides bonuses for completed research.
 */
package medievalsim.guilds.research;

import medievalsim.config.ModConfig;
import medievalsim.guilds.GuildAuditEntry;
import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildRank;
import medievalsim.util.ModLogger;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

import java.util.*;

/**
 * Manages a guild's research state - active projects, completed research, and bonuses.
 */
public class GuildResearchManager {

    // Owning guild
    private final int guildID;

    // Completed research projects
    private final Set<String> completedResearch = new HashSet<>();

    // Currently active research (null if none)
    private String activeResearchID = null;
    private long researchProgress = 0;  // Points accumulated toward active research

    // Accumulated research points (from scientist pulls, donations, etc.)
    private long researchPoints = 0;

    // Cached bonuses from completed research
    private final Map<ResearchEffect.EffectType, Float> bonusCache = new HashMap<>();
    private boolean bonusCacheDirty = true;

    public GuildResearchManager(int guildID) {
        this.guildID = guildID;
    }

    // === Research Progress ===

    /**
     * Add research points (from scientist, donations, daily passive, etc.)
     */
    public void addResearchPoints(long points) {
        if (points <= 0) return;
        researchPoints += points;

        // If research is active, apply points
        if (activeResearchID != null) {
            applyResearchPoints();
        }
    }

    /**
     * Apply accumulated points to active research.
     */
    private void applyResearchPoints() {
        if (activeResearchID == null || researchPoints <= 0) return;

        ResearchProject project = ResearchRegistry.getProject(activeResearchID);
        if (project == null) return;

        long needed = project.getResearchPointCost() - researchProgress;
        long toApply = Math.min(researchPoints, needed);

        researchProgress += toApply;
        researchPoints -= toApply;
    }

    /**
     * Start a new research project.
     */
    public boolean startResearch(String projectID, GuildData guild, long playerAuth) {
        if (activeResearchID != null) {
            return false;  // Already researching
        }

        ResearchProject project = ResearchRegistry.getProject(projectID);
        if (project == null) {
            return false;
        }

        // Check prerequisites
        if (!project.hasPrerequisites(completedResearch)) {
            return false;
        }

        // Check already completed
        if (completedResearch.contains(projectID)) {
            return false;
        }

        // Check coin cost
        if (guild.getTreasury() < project.getCoinCost()) {
            return false;
        }

        // Deduct cost
        guild.withdrawTreasury(project.getCoinCost());

        // Start research
        activeResearchID = projectID;
        researchProgress = 0;

        // Apply any accumulated points
        applyResearchPoints();

        // Audit log
        guild.addAuditEntry(new GuildAuditEntry(
            GuildAuditEntry.Action.RESEARCH_STARTED,
            playerAuth,
            "Started research: " + projectID + " (cost: " + project.getCoinCost() + ")"
        ));

        ModLogger.info("Guild %d started research: %s", guildID, projectID);
        return true;
    }

    /**
     * Cancel active research (no refund).
     */
    public void cancelResearch(GuildData guild, long playerAuth) {
        if (activeResearchID == null) return;

        String cancelled = activeResearchID;
        activeResearchID = null;
        researchProgress = 0;

        guild.addAuditEntry(new GuildAuditEntry(
            GuildAuditEntry.Action.RESEARCH_STARTED,  // Using same action, could add RESEARCH_CANCELLED
            playerAuth,
            "Cancelled research: " + cancelled
        ));
    }

    /**
     * Check and complete research if finished.
     * @return true if research was completed
     */
    public boolean checkCompletion(GuildData guild) {
        if (activeResearchID == null) return false;

        ResearchProject project = ResearchRegistry.getProject(activeResearchID);
        if (project == null) {
            activeResearchID = null;
            return false;
        }

        if (researchProgress >= project.getResearchPointCost()) {
            // Complete!
            completedResearch.add(activeResearchID);
            bonusCacheDirty = true;

            // Apply effects
            applyResearchEffect(project.getEffect(), guild);

            guild.addAuditEntry(new GuildAuditEntry(
                GuildAuditEntry.Action.RESEARCH_COMPLETED,
                0,  // System action
                "Completed research: " + activeResearchID
            ));

            ModLogger.info("Guild %d completed research: %s", guildID, activeResearchID);

            activeResearchID = null;
            researchProgress = 0;
            return true;
        }

        return false;
    }

    /**
     * Apply a research effect immediately upon completion.
     */
    private void applyResearchEffect(ResearchEffect effect, GuildData guild) {
        switch (effect.getType()) {
            case BANK_TAB_UNLOCK -> {
                if (guild.getBank() != null) {
                    guild.getBank().unlockTab();
                }
            }
            case MEMBER_CAPACITY -> {
                // Bonus is tracked in cache, no immediate action needed
            }
            // Other effects are passive bonuses, tracked via getBonus()
            default -> {}
        }
    }

    // === Bonus Calculations ===

    /**
     * Get the total bonus for a specific effect type.
     */
    public float getBonus(ResearchEffect.EffectType type) {
        if (bonusCacheDirty) {
            rebuildBonusCache();
        }
        return bonusCache.getOrDefault(type, 0f);
    }

    /**
     * Rebuild the bonus cache from completed research.
     */
    private void rebuildBonusCache() {
        bonusCache.clear();

        for (String projectID : completedResearch) {
            ResearchProject project = ResearchRegistry.getProject(projectID);
            if (project != null && project.getEffect() != null) {
                ResearchEffect effect = project.getEffect();
                float current = bonusCache.getOrDefault(effect.getType(), 0f);
                bonusCache.put(effect.getType(), current + effect.getValue());
            }
        }

        bonusCacheDirty = false;
    }

    /**
     * Check if a specific research is completed.
     */
    public boolean hasCompleted(String projectID) {
        return completedResearch.contains(projectID);
    }

    /**
     * Check if a feature is unlocked via research.
     */
    public boolean hasUnlocked(ResearchEffect.EffectType unlockType) {
        return getBonus(unlockType) > 0;
    }

    // === State Access ===

    public String getActiveResearchID() {
        return activeResearchID;
    }

    public long getResearchProgress() {
        return researchProgress;
    }

    public long getResearchPoints() {
        return researchPoints;
    }

    public Set<String> getCompletedResearch() {
        return new HashSet<>(completedResearch);
    }

    /**
     * Get progress as percentage (0-100).
     */
    public int getProgressPercent() {
        if (activeResearchID == null) return 0;
        ResearchProject project = ResearchRegistry.getProject(activeResearchID);
        if (project == null || project.getResearchPointCost() == 0) return 0;
        return (int) (researchProgress * 100 / project.getResearchPointCost());
    }

    /**
     * Get available research projects (not completed, prerequisites met).
     */
    public List<ResearchProject> getAvailableProjects() {
        List<ResearchProject> available = new ArrayList<>();
        for (ResearchProject project : ResearchRegistry.getAllProjects()) {
            if (!completedResearch.contains(project.getId()) &&
                project.hasPrerequisites(completedResearch)) {
                available.add(project);
            }
        }
        return available;
    }

    // === Persistence ===

    public void addSaveData(SaveData save) {
        // Completed research - save as nested data
        SaveData completedSave = new SaveData("completedResearch");
        int i = 0;
        for (String researchID : completedResearch) {
            completedSave.addUnsafeString("res" + i, researchID);
            i++;
        }
        completedSave.addInt("count", i);
        save.addSaveData(completedSave);

        // Active research
        save.addUnsafeString("activeResearchID", activeResearchID != null ? activeResearchID : "");
        save.addLong("researchProgress", researchProgress);
        save.addLong("researchPoints", researchPoints);
    }

    public void applyLoadData(LoadData load) {
        completedResearch.clear();
        LoadData completedLoad = load.getFirstLoadDataByName("completedResearch");
        if (completedLoad != null) {
            int count = completedLoad.getInt("count", 0);
            for (int i = 0; i < count; i++) {
                String researchID = completedLoad.getUnsafeString("res" + i, null);
                if (researchID != null && !researchID.isEmpty()) {
                    completedResearch.add(researchID);
                }
            }
        }

        activeResearchID = load.getUnsafeString("activeResearchID", "");
        if (activeResearchID.isEmpty()) activeResearchID = null;
        researchProgress = load.getLong("researchProgress", 0);
        researchPoints = load.getLong("researchPoints", 0);

        bonusCacheDirty = true;
    }

    // === Network ===

    public void writePacket(PacketWriter writer) {
        writer.putNextInt(completedResearch.size());
        for (String id : completedResearch) {
            writer.putNextString(id);
        }

        writer.putNextString(activeResearchID != null ? activeResearchID : "");
        writer.putNextLong(researchProgress);
        writer.putNextLong(researchPoints);
    }

    public void readPacket(PacketReader reader) {
        completedResearch.clear();
        int count = reader.getNextInt();
        for (int i = 0; i < count; i++) {
            completedResearch.add(reader.getNextString());
        }

        activeResearchID = reader.getNextString();
        if (activeResearchID.isEmpty()) activeResearchID = null;
        researchProgress = reader.getNextLong();
        researchPoints = reader.getNextLong();

        bonusCacheDirty = true;
    }
}
