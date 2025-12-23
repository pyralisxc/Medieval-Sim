package medievalsim;

import medievalsim.config.UnifiedMedievalSimSettings;
import medievalsim.guilds.GuildSystemRegistry;
import medievalsim.registries.MedievalSimBuffs;
import medievalsim.registries.MedievalSimAdminCommands;
import medievalsim.registries.MedievalSimContainers;
import medievalsim.registries.MedievalSimControls;
import medievalsim.registries.MedievalSimLevelData;
import medievalsim.registries.MedievalSimObjects;
import medievalsim.registries.MedievalSimPackets;
import medievalsim.registries.MedievalSimRecipes;
import medievalsim.registries.MedievalSimWorldData;
import medievalsim.util.ModLogger;
import necesse.engine.modLoader.ModSettings;
import necesse.engine.modLoader.annotations.ModEntry;

@ModEntry
public class MedievalSim {
    public void init() {
        // Register WorldData classes first (GuildManager, etc.)
        MedievalSimWorldData.registerCore();
        
        MedievalSimObjects.registerCore();
        MedievalSimRecipes.registerCore();
        MedievalSimLevelData.registerCore();
        MedievalSimPackets.registerCore();
        MedievalSimContainers.registerCore();
        MedievalSimControls.registerCore();
        MedievalSimBuffs.registerCore();
        
        // Register guild system (items, mobs, objects, containers)
        GuildSystemRegistry.registerAll();

        ModLogger.info("Medieval Sim initialized");
        this.applySettingsToRuntime();
    }

    public void initResources() {
        // Load textures and other client-side resources for modular systems
        GuildSystemRegistry.loadTextures();
    }

    public void postInit() {
        // Initialize command center wrapper system
        medievalsim.commandcenter.wrapper.NecesseCommandRegistry.initialize();
        // Register admin commands such as order-book debug commands
        MedievalSimAdminCommands.registerCore();
    }

    public ModSettings initSettings() {
        return new UnifiedMedievalSimSettings();
    }

    public void applySettingsToRuntime() {
        // With the unified configuration system, settings are automatically applied
        // when loaded. No need for complex runtime application logic!
    }
}

