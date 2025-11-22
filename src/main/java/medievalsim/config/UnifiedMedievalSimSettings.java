package medievalsim.config;

import necesse.engine.modLoader.ModSettings;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

/**
 * Thin adapter between Necesse's ModSettings system and our config architecture.
 *
 * This class simply delegates to:
 * - ModConfig (for configuration values)
 * - SettingsManager (for user data like favorites, history, UI state)
 *
 * ARCHITECTURE:
 * - ModConfig: Static configuration values (limits, defaults, etc.)
 * - SettingsManager: Instance-based user data (favorites, history, UI state)
 * - UnifiedMedievalSimSettings: Necesse integration layer (save/load)
 */
public class UnifiedMedievalSimSettings extends ModSettings {

    @Override
    public void addSaveData(SaveData save) {
        // Save configuration values
        ModConfig.saveToData(save);

        // Save user data
        SettingsManager.getInstance().saveToData(save);
    }

    @Override
    public void applyLoadData(LoadData save) {
        // Load configuration values
        ModConfig.loadFromData(save);

        // Load user data
        SettingsManager.getInstance().loadFromData(save);
    }
}
