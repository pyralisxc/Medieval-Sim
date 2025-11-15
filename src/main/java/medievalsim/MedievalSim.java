package medievalsim;

import medievalsim.config.ModConfig;
import medievalsim.config.UnifiedMedievalSimSettings;
import medievalsim.registries.MedievalSimBuffs;
import medievalsim.registries.MedievalSimControls;
import medievalsim.registries.MedievalSimLevelData;
import medievalsim.registries.MedievalSimObjects;
import medievalsim.registries.MedievalSimPackets;
import medievalsim.util.ModLogger;
import medievalsim.zones.events.ZoneEventBus;
import medievalsim.zones.events.listeners.ZoneLoggingListener;
import necesse.engine.modLoader.ModSettings;
import necesse.engine.modLoader.annotations.ModEntry;

@ModEntry
public class MedievalSim {
    public void init() {
        ModLogger.info("Initializing mod...");
        MedievalSimObjects.registerCore();
        MedievalSimLevelData.registerCore();
        MedievalSimPackets.registerCore();
        MedievalSimControls.registerCore();
        MedievalSimBuffs.registerCore();
        
        // Initialize zone event system
        initializeZoneEventSystem();
        
        ModLogger.info("Mod initialized successfully!");
        // Apply saved mod settings to runtime-configurable constants
        this.applySettingsToRuntime();
    }

    public void initResources() {
    }

    public void postInit() {
        ModLogger.info("Post-initialization: Scanning Necesse commands...");
        // Initialize command center wrapper system
        medievalsim.commandcenter.wrapper.NecesseCommandRegistry.initialize();
    }

    public ModSettings initSettings() {
        ModLogger.info("Loading unified mod settings...");
        return new UnifiedMedievalSimSettings();
    }

    public void applySettingsToRuntime() {
        // With the unified configuration system, settings are automatically applied
        // when loaded. No need for complex runtime application logic!
        
        ModLogger.info("Configuration system initialized");
        ModLogger.debug(ModConfig.getConfigSummary());
    }
    
    /**
     * Initialize the zone event system with default listeners.
     */
    private void initializeZoneEventSystem() {
        try {
            // Register the zone logging listener for admin activity tracking
            ZoneLoggingListener loggingListener = new ZoneLoggingListener();
            ZoneEventBus.getInstance().register(loggingListener);
            
            ModLogger.info("Zone event system initialized with %d listeners", 
                ZoneEventBus.getInstance().getListenerCount());
            ModLogger.debug("Registered listeners: %s", loggingListener.getDescription());
        } catch (Exception e) {
            ModLogger.error("Failed to initialize zone event system: %s", e.getMessage());
        }
    }
}

