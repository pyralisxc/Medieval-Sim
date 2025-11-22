package medievalsim;

import medievalsim.config.UnifiedMedievalSimSettings;
import medievalsim.registries.MedievalSimBuffs;
import medievalsim.registries.MedievalSimControls;
import medievalsim.registries.MedievalSimLevelData;
import medievalsim.registries.MedievalSimObjects;
import medievalsim.registries.MedievalSimPackets;
import medievalsim.util.ModLogger;
import necesse.engine.modLoader.ModSettings;
import necesse.engine.modLoader.annotations.ModEntry;

@ModEntry
public class MedievalSim {
    public void init() {
        ModLogger.debug("Initializing mod...");
        MedievalSimObjects.registerCore();
        MedievalSimLevelData.registerCore();
        MedievalSimPackets.registerCore();
        MedievalSimControls.registerCore();
        MedievalSimBuffs.registerCore();
        
        ModLogger.info("Medieval Sim initialized");
        // Apply saved mod settings to runtime-configurable constants
        this.applySettingsToRuntime();
    }

    public void initResources() {
    }

    public void postInit() {
        ModLogger.debug("Post-initialization: Scanning Necesse commands...");
        // Initialize command center wrapper system
        medievalsim.commandcenter.wrapper.NecesseCommandRegistry.initialize();
    }

    public ModSettings initSettings() {
        ModLogger.debug("Loading unified mod settings...");
        return new UnifiedMedievalSimSettings();
    }

    public void applySettingsToRuntime() {
        // With the unified configuration system, settings are automatically applied
        // when loaded. No need for complex runtime application logic!
        
        ModLogger.debug("Configuration system initialized");
    }
}

