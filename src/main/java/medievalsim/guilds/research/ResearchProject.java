/*
 * Research Project for Medieval Sim Mod
 * Defines a single research project that guilds can complete for bonuses.
 */
package medievalsim.guilds.research;

import medievalsim.guilds.GuildRank;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a research project that can be completed for guild bonuses.
 * Research projects have tiers, costs, and prerequisites.
 */
public class ResearchProject {

    private final String id;
    private final String category;
    private final String nameKey;  // Localization key for display name
    private final String descKey;  // Localization key for description

    // Requirements
    private final int tier;
    private final long coinCost;
    private final long researchPointCost;
    private final List<String> prerequisites;  // IDs of required projects
    private final GuildRank minRankToStart;

    // Effects when completed
    private final ResearchEffect effect;

    public ResearchProject(String id, String category, String nameKey, String descKey,
                          int tier, long coinCost, long researchPointCost,
                          List<String> prerequisites, GuildRank minRankToStart,
                          ResearchEffect effect) {
        this.id = id;
        this.category = category;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.tier = tier;
        this.coinCost = coinCost;
        this.researchPointCost = researchPointCost;
        this.prerequisites = prerequisites != null ? new ArrayList<>(prerequisites) : new ArrayList<>();
        this.minRankToStart = minRankToStart;
        this.effect = effect;
    }

    // === Getters ===

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getDescKey() {
        return descKey;
    }

    public int getTier() {
        return tier;
    }

    public long getCoinCost() {
        return coinCost;
    }

    public long getResearchPointCost() {
        return researchPointCost;
    }

    public List<String> getPrerequisites() {
        return new ArrayList<>(prerequisites);
    }

    public GuildRank getMinRankToStart() {
        return minRankToStart;
    }

    public ResearchEffect getEffect() {
        return effect;
    }

    /**
     * Check if all prerequisites are met.
     */
    public boolean hasPrerequisites(java.util.Set<String> completedResearch) {
        for (String prereq : prerequisites) {
            if (!completedResearch.contains(prereq)) {
                return false;
            }
        }
        return true;
    }

    // === Network ===

    public void writePacket(PacketWriter writer) {
        writer.putNextString(id);
        writer.putNextString(category);
        writer.putNextString(nameKey);
        writer.putNextString(descKey);
        writer.putNextInt(tier);
        writer.putNextLong(coinCost);
        writer.putNextLong(researchPointCost);
        writer.putNextInt(prerequisites.size());
        for (String prereq : prerequisites) {
            writer.putNextString(prereq);
        }
        writer.putNextInt(minRankToStart.getLevel());
        effect.writePacket(writer);
    }

    public static ResearchProject readPacket(PacketReader reader) {
        String id = reader.getNextString();
        String category = reader.getNextString();
        String nameKey = reader.getNextString();
        String descKey = reader.getNextString();
        int tier = reader.getNextInt();
        long coinCost = reader.getNextLong();
        long researchPointCost = reader.getNextLong();
        int prereqCount = reader.getNextInt();
        List<String> prerequisites = new ArrayList<>();
        for (int i = 0; i < prereqCount; i++) {
            prerequisites.add(reader.getNextString());
        }
        GuildRank minRank = GuildRank.fromLevel(reader.getNextInt());
        ResearchEffect effect = ResearchEffect.readPacket(reader);

        return new ResearchProject(id, category, nameKey, descKey, tier, coinCost,
            researchPointCost, prerequisites, minRank, effect);
    }

    @Override
    public String toString() {
        return "ResearchProject{id='" + id + "', tier=" + tier + ", cost=" + coinCost + "}";
    }
}
