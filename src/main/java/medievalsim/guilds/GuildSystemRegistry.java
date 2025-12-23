/*
 * Guild System Registration for Medieval Sim Mod
 * Central registration point for all guild-related components.
 */
package medievalsim.guilds;

import medievalsim.guilds.bank.GuildBankContainer;
import medievalsim.guilds.crest.GuildCrestRenderer;
import medievalsim.guilds.items.GuildCrestItem;
import medievalsim.guilds.items.GuildArtisanSpawnItem;
import necesse.engine.registries.ItemRegistry;
import medievalsim.guilds.mobs.GuildArtisanContainer;
import medievalsim.guilds.mobs.GuildArtisanMob;
import medievalsim.guilds.objects.GuildBannerObject;
import medievalsim.guilds.objects.GuildCauldronObject;
import medievalsim.guilds.objects.GuildChestObject;
import medievalsim.guilds.objects.GuildFlagObject;
import medievalsim.guilds.objects.GuildTeleportBannerObject;
import medievalsim.guilds.research.GuildResearchContainer;
import medievalsim.guilds.research.ResearchRegistry;
import medievalsim.guilds.teleport.GuildTeleportContainer;
import medievalsim.util.ModLogger;
import necesse.engine.registries.MobRegistry;
import necesse.gfx.GameResources;

/**
 * Handles registration of all guild system components.
 * Called during mod initialization.
 */
public final class GuildSystemRegistry {

    private GuildSystemRegistry() {}

    /**
     * Register all guild system components.
     * Call this from the main mod initialization.
     */
    public static void registerAll() {
        ModLogger.info("Initializing Guild System...");

        // Phase 0: Core data structures are auto-initialized
        // GuildRank, PermissionType, GuildData, etc. are enums/classes
        // GuildManager is WorldData that registers on-demand
        // Guild zones are now managed per-level via AdminZonesLevelData

        // Register containers
        registerContainers();

        // Register mobs
        registerMobs();

        // Register objects
        registerObjects();

        // Register items
        registerItems();

        // Register research projects
        ResearchRegistry.registerDefaultProjects();

        ModLogger.info("Guild System initialization complete");
    }

    /**
     * Register containers for UI interactions.
     */
    private static void registerContainers() {
        ModLogger.debug("Registering guild containers...");
        GuildArtisanContainer.registerContainer();
        GuildBankContainer.registerContainer();
        GuildResearchContainer.registerContainer();
        GuildTeleportContainer.registerContainer();
        
        // New containers per docs refactor
        medievalsim.guilds.ui.ManageGuildsContainer.registerContainer();
        medievalsim.guilds.ui.NotificationsContainer.registerContainer();
        medievalsim.guilds.ui.BuyBannerContainer.registerContainer();
    }

    /**
     * Register mob types.
     */
    private static void registerMobs() {
        ModLogger.debug("Registering guild mobs...");
        GuildArtisanMob.registerMob();
    }

    /**
     * Register game objects.
     */
    private static void registerObjects() {
        ModLogger.debug("Registering guild objects...");
        GuildFlagObject.registerObject();
        GuildBannerObject.registerObject();
        GuildChestObject.registerObject();
        GuildTeleportBannerObject.registerObject();
        // Guild cauldron is disabled until brewing system is implemented
        // GuildCauldronObject.registerObject();
    }

    /**
     * Register items.
     */
    private static void registerItems() {
        ModLogger.debug("Registering guild items...");
        // Guild banner is now an object, not an item
        GuildCrestItem.registerItem();

        // Register a custom spawn item for the Guild Artisan so the icon size/appearance is correct
        ItemRegistry.registerItem("guildartisanspawnitem", new GuildArtisanSpawnItem(), 50.0f, true);
    }

    /**
     * Load textures (call during texture loading phase).
     */
    public static void loadTextures() {
        ModLogger.debug("Loading guild textures...");
        GuildCrestRenderer.loadTextures();
        // Load mob textures so spawn item icons and client-side rendering work
        GuildArtisanMob.loadTextures();
        // Debug: log whether the mob icon was loaded (helps verify creative spawn icon)
        try {
            boolean hasIcon = MobRegistry.getMobIcon("guildartisan") != necesse.gfx.GameResources.error;
            ModLogger.info("Guild artisan mob icon loaded: %b", hasIcon);
        } catch (Exception e) {
            ModLogger.info("Guild artisan mob icon check failed: %s", e.getMessage());
        }
    }

    /**
     * Register recipes for guild items.
     * Call after item registration.
     */
    public static void registerRecipes() {
        ModLogger.debug("Registering guild recipes...");

        // Guild Flag recipe (requires workstation)
        // Example: 10 wood, 5 cloth, 1 gold bar
        // Recipes.registerModRecipe(new Recipe(
        //     "guildflag",
        //     RecipeTechRegistry.WORKSTATION,
        //     new Ingredient[]{
        //         new Ingredient("wood", 10),
        //         new Ingredient("cloth", 5),
        //         new Ingredient("goldbar", 1)
        //     }
        // ));

        // Guild Chest recipe
        // Example: 20 wood, 10 iron bar
        // Recipes.registerModRecipe(new Recipe(
        //     "guildchest",
        //     RecipeTechRegistry.WORKSTATION,
        //     new Ingredient[]{
        //         new Ingredient("wood", 20),
        //         new Ingredient("ironbar", 10)
        //     }
        // ));

        // Guild Banner - crafted at Guild Artisan NPC
        // Guild Crest - crafted at Guild Artisan NPC
    }
}
