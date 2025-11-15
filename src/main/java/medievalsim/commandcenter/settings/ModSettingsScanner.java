package medievalsim.commandcenter.settings;

import necesse.engine.modLoader.LoadedMod;
import necesse.engine.modLoader.ModLoader;
import necesse.engine.modLoader.ModSettings;

import java.util.*;

/**
 * Scans all loaded mods for their ModSettings to enable cross-mod configuration
 */
public class ModSettingsScanner {
    
    /**
     * Scan all loaded mods and discover their settings
     * @return Map of mod name to list of mod settings
     */
    public static Map<String, ModSettingsInfo> scanAllModSettings() {
        Map<String, ModSettingsInfo> modSettingsMap = new LinkedHashMap<>();
        
        for (LoadedMod mod : ModLoader.getEnabledMods()) {
            if (mod == null) continue;
            
            ModSettings settings = mod.getSettings();
            if (settings != null) {
                ModSettingsInfo info = new ModSettingsInfo(
                    mod.name,
                    mod.id,
                    mod.version,
                    settings
                );
                modSettingsMap.put(mod.id, info);
            }
        }
        
        return modSettingsMap;
    }
    
    /**
     * Get settings for a specific mod
     */
    public static ModSettings getModSettings(String modId) {
        for (LoadedMod mod : ModLoader.getEnabledMods()) {
            if (mod != null && mod.id.equals(modId)) {
                return mod.getSettings();
            }
        }
        return null;
    }
    
    /**
     * Container for mod settings information
     */
    public static class ModSettingsInfo {
        private final String modName;
        private final String modId;
        private final String modVersion;
        private final ModSettings settings;
        
        public ModSettingsInfo(String modName, String modId, String modVersion, ModSettings settings) {
            this.modName = modName;
            this.modId = modId;
            this.modVersion = modVersion;
            this.settings = settings;
        }
        
        public String getModName() { return modName; }
        public String getModId() { return modId; }
        public String getModVersion() { return modVersion; }
        public ModSettings getSettings() { return settings; }
        
        /**
         * Get display name for UI
         */
        public String getDisplayName() {
            return modName + " v" + modVersion;
        }
    }
}
