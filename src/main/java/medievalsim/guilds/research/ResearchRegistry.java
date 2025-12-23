/*
 * Research Registry for Medieval Sim Mod
 * Defines all available research projects and their effects.
 */
package medievalsim.guilds.research;

import medievalsim.guilds.GuildRank;
import medievalsim.util.ModLogger;

import java.util.*;

/**
 * Registry of all research projects available to guilds.
 * Projects are organized by category and tier.
 */
public final class ResearchRegistry {

    // All registered projects
    private static final Map<String, ResearchProject> projects = new LinkedHashMap<>();

    // Categories
    public static final String CATEGORY_INFRASTRUCTURE = "infrastructure";
    public static final String CATEGORY_MILITARY = "military";
    public static final String CATEGORY_ECONOMY = "economy";
    public static final String CATEGORY_TERRITORY = "territory";
    public static final String CATEGORY_SPECIAL = "special";

    private ResearchRegistry() {}

    // === Registration ===

    public static void registerProject(ResearchProject project) {
        projects.put(project.getId(), project);
    }

    public static ResearchProject getProject(String id) {
        return projects.get(id);
    }

    public static Collection<ResearchProject> getAllProjects() {
        return Collections.unmodifiableCollection(projects.values());
    }

    public static List<ResearchProject> getProjectsByCategory(String category) {
        List<ResearchProject> result = new ArrayList<>();
        for (ResearchProject project : projects.values()) {
            if (project.getCategory().equals(category)) {
                result.add(project);
            }
        }
        return result;
    }

    public static List<ResearchProject> getProjectsByTier(int tier) {
        List<ResearchProject> result = new ArrayList<>();
        for (ResearchProject project : projects.values()) {
            if (project.getTier() == tier) {
                result.add(project);
            }
        }
        return result;
    }

    // === Initialization ===

    /**
     * Register all default research projects.
     * Called during mod initialization.
     */
    public static void registerDefaultProjects() {
        ModLogger.info("Registering guild research projects...");

        // === INFRASTRUCTURE (Bank, Storage, Members) ===

        // Tier 1 Infrastructure
        registerProject(new ResearchProject(
            "bank_expansion_1",
            CATEGORY_INFRASTRUCTURE,
            "research.bank_expansion_1.name",
            "research.bank_expansion_1.desc",
            1,  // tier
            25000,  // coin cost
            1000,  // research points
            null,  // no prereqs
            GuildRank.OFFICER,
            ResearchEffect.bankTab()
        ));

        registerProject(new ResearchProject(
            "member_capacity_1",
            CATEGORY_INFRASTRUCTURE,
            "research.member_capacity_1.name",
            "research.member_capacity_1.desc",
            1,
            20000,
            800,
            null,
            GuildRank.OFFICER,
            ResearchEffect.memberCapacity(10)
        ));

        // Tier 2 Infrastructure
        registerProject(new ResearchProject(
            "bank_expansion_2",
            CATEGORY_INFRASTRUCTURE,
            "research.bank_expansion_2.name",
            "research.bank_expansion_2.desc",
            2,
            75000,
            3000,
            List.of("bank_expansion_1"),
            GuildRank.OFFICER,
            ResearchEffect.bankTab()
        ));

        registerProject(new ResearchProject(
            "member_capacity_2",
            CATEGORY_INFRASTRUCTURE,
            "research.member_capacity_2.name",
            "research.member_capacity_2.desc",
            2,
            50000,
            2000,
            List.of("member_capacity_1"),
            GuildRank.OFFICER,
            ResearchEffect.memberCapacity(15)
        ));

        // Tier 3 Infrastructure
        registerProject(new ResearchProject(
            "bank_expansion_3",
            CATEGORY_INFRASTRUCTURE,
            "research.bank_expansion_3.name",
            "research.bank_expansion_3.desc",
            3,
            150000,
            6000,
            List.of("bank_expansion_2"),
            GuildRank.LEADER,
            ResearchEffect.bankTab()
        ));

        // === MILITARY (Combat Bonuses) ===

        // Tier 1 Military
        registerProject(new ResearchProject(
            "combat_training_1",
            CATEGORY_MILITARY,
            "research.combat_training_1.name",
            "research.combat_training_1.desc",
            1,
            30000,
            1200,
            null,
            GuildRank.OFFICER,
            ResearchEffect.damageBonusPercent(5f)
        ));

        registerProject(new ResearchProject(
            "fortification_1",
            CATEGORY_MILITARY,
            "research.fortification_1.name",
            "research.fortification_1.desc",
            1,
            30000,
            1200,
            null,
            GuildRank.OFFICER,
            new ResearchEffect(ResearchEffect.EffectType.COMBAT_DEFENSE_BONUS, 5f, true)
        ));

        // Tier 2 Military
        registerProject(new ResearchProject(
            "combat_training_2",
            CATEGORY_MILITARY,
            "research.combat_training_2.name",
            "research.combat_training_2.desc",
            2,
            80000,
            3500,
            List.of("combat_training_1"),
            GuildRank.OFFICER,
            ResearchEffect.damageBonusPercent(10f)
        ));

        registerProject(new ResearchProject(
            "healing_aura",
            CATEGORY_MILITARY,
            "research.healing_aura.name",
            "research.healing_aura.desc",
            2,
            60000,
            2500,
            List.of("fortification_1"),
            GuildRank.OFFICER,
            new ResearchEffect(ResearchEffect.EffectType.COMBAT_REGEN_BONUS, 2f, false)
        ));

        // === ECONOMY (Crafting, Gathering, Income) ===

        // Tier 1 Economy
        registerProject(new ResearchProject(
            "efficient_crafting_1",
            CATEGORY_ECONOMY,
            "research.efficient_crafting_1.name",
            "research.efficient_crafting_1.desc",
            1,
            25000,
            1000,
            null,
            GuildRank.OFFICER,
            ResearchEffect.craftingSpeedPercent(10f)
        ));

        registerProject(new ResearchProject(
            "resource_mastery_1",
            CATEGORY_ECONOMY,
            "research.resource_mastery_1.name",
            "research.resource_mastery_1.desc",
            1,
            25000,
            1000,
            null,
            GuildRank.OFFICER,
            new ResearchEffect(ResearchEffect.EffectType.GATHERING_BONUS, 5f, true)
        ));

        // Tier 2 Economy
        registerProject(new ResearchProject(
            "treasury_interest",
            CATEGORY_ECONOMY,
            "research.treasury_interest.name",
            "research.treasury_interest.desc",
            2,
            100000,
            4000,
            List.of("efficient_crafting_1"),
            GuildRank.LEADER,
            new ResearchEffect(ResearchEffect.EffectType.TREASURY_INTEREST, 0.1f, true)
        ));

        registerProject(new ResearchProject(
            "passive_income",
            CATEGORY_ECONOMY,
            "research.passive_income.name",
            "research.passive_income.desc",
            2,
            75000,
            3000,
            List.of("resource_mastery_1"),
            GuildRank.OFFICER,
            ResearchEffect.passiveIncome(500)
        ));

        // === TERRITORY (Expansion, Control) ===

        // Tier 1 Territory
        registerProject(new ResearchProject(
            "territory_expansion_1",
            CATEGORY_TERRITORY,
            "research.territory_expansion_1.name",
            "research.territory_expansion_1.desc",
            1,
            40000,
            1500,
            null,
            GuildRank.OFFICER,
            ResearchEffect.territorySize(500)
        ));

        // Tier 2 Territory
        registerProject(new ResearchProject(
            "territory_expansion_2",
            CATEGORY_TERRITORY,
            "research.territory_expansion_2.name",
            "research.territory_expansion_2.desc",
            2,
            100000,
            4000,
            List.of("territory_expansion_1"),
            GuildRank.OFFICER,
            ResearchEffect.territorySize(1000)
        ));

        registerProject(new ResearchProject(
            "additional_plots",
            CATEGORY_TERRITORY,
            "research.additional_plots.name",
            "research.additional_plots.desc",
            2,
            80000,
            3500,
            List.of("territory_expansion_1"),
            GuildRank.LEADER,
            new ResearchEffect(ResearchEffect.EffectType.TERRITORY_PLOTS, 2f, false)
        ));

        // === SPECIAL (Unlocks) ===

        // Tier 2 Special
        registerProject(new ResearchProject(
            "unlock_alliances",
            CATEGORY_SPECIAL,
            "research.unlock_alliances.name",
            "research.unlock_alliances.desc",
            2,
            50000,
            2000,
            List.of("member_capacity_1"),
            GuildRank.LEADER,
            new ResearchEffect(ResearchEffect.EffectType.UNLOCK_ALLY_SYSTEM, 1f, false)
        ));

        // Tier 3 Special
        registerProject(new ResearchProject(
            "unlock_guild_shop",
            CATEGORY_SPECIAL,
            "research.unlock_guild_shop.name",
            "research.unlock_guild_shop.desc",
            3,
            200000,
            8000,
            List.of("efficient_crafting_1", "resource_mastery_1"),
            GuildRank.LEADER,
            new ResearchEffect(ResearchEffect.EffectType.UNLOCK_GUILD_SHOP, 1f, false)
        ));

        registerProject(new ResearchProject(
            "unlock_guild_quests",
            CATEGORY_SPECIAL,
            "research.unlock_guild_quests.name",
            "research.unlock_guild_quests.desc",
            3,
            150000,
            6000,
            List.of("member_capacity_2"),
            GuildRank.LEADER,
            new ResearchEffect(ResearchEffect.EffectType.UNLOCK_GUILD_QUESTS, 1f, false)
        ));

        ModLogger.info("Registered %d guild research projects", projects.size());
    }

    /**
     * Get all category IDs.
     */
    public static List<String> getCategories() {
        return List.of(
            CATEGORY_INFRASTRUCTURE,
            CATEGORY_MILITARY,
            CATEGORY_ECONOMY,
            CATEGORY_TERRITORY,
            CATEGORY_SPECIAL
        );
    }

    /**
     * Get localization key for category name.
     */
    public static String getCategoryNameKey(String category) {
        return "research.category." + category;
    }
}
