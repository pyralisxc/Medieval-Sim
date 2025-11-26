package medievalsim;

import medievalsim.config.UnifiedMedievalSimSettings;
import medievalsim.registries.MedievalSimBuffs;
import medievalsim.registries.MedievalSimContainers;
import medievalsim.registries.MedievalSimControls;
import medievalsim.registries.MedievalSimLevelData;
import medievalsim.registries.MedievalSimObjects;
import medievalsim.registries.MedievalSimPackets;
import medievalsim.registries.MedievalSimRecipes;
import medievalsim.util.ModLogger;
import necesse.engine.modLoader.ModSettings;
import necesse.engine.modLoader.annotations.ModEntry;

@ModEntry
public class MedievalSim {
    public void init() {
        MedievalSimObjects.registerCore();
        MedievalSimRecipes.registerCore();
        MedievalSimLevelData.registerCore();
        MedievalSimPackets.registerCore();
        MedievalSimContainers.registerCore();
        MedievalSimControls.registerCore();
        MedievalSimBuffs.registerCore();

        ModLogger.info("Medieval Sim initialized");
        this.applySettingsToRuntime();
    }

    public void initResources() {
    }

    public void postInit() {
        // Initialize command center wrapper system
        medievalsim.commandcenter.wrapper.NecesseCommandRegistry.initialize();
    }

    public ModSettings initSettings() {
        return new UnifiedMedievalSimSettings();
    }

    public void applySettingsToRuntime() {
        // With the unified configuration system, settings are automatically applied
        // when loaded. No need for complex runtime application logic!
    }
}

