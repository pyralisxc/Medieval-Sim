package medievalsim.commandcenter.settings;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all Command Center settings
 */
public class SettingsRegistry {
    
    private static final Map<String, AdminSetting<?>> settings = new ConcurrentHashMap<>();
    private static final Map<String, List<AdminSetting<?>>> settingsByCategory = new ConcurrentHashMap<>();
    private static boolean initialized = false;
    
    /**
     * Initialize and scan all settings
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        
        Map<String, List<AdminSetting<?>>> scannedSettings = SettingsScanner.scanAllSettings();
        
        for (Map.Entry<String, List<AdminSetting<?>>> entry : scannedSettings.entrySet()) {
            String category = entry.getKey();
            List<AdminSetting<?>> categorySettings = entry.getValue();
            
            settingsByCategory.put(category, categorySettings);
            
            for (AdminSetting<?> setting : categorySettings) {
                settings.put(setting.getId(), setting);
            }
        }
        
        initialized = true;
    }
    
    /**
     * Get a setting by ID
     */
    public static AdminSetting<?> getSetting(String id) {
        return settings.get(id);
    }
    
    /**
     * Get all settings
     */
    public static Collection<AdminSetting<?>> getAllSettings() {
        return settings.values();
    }
    
    /**
     * Get settings by category
     */
    public static List<AdminSetting<?>> getSettingsByCategory(String category) {
        return settingsByCategory.getOrDefault(category, Collections.emptyList());
    }
    
    /**
     * Get all categories
     */
    public static Set<String> getCategories() {
        return settingsByCategory.keySet();
    }
    
    /**
     * Get all hot-reload settings
     */
    public static List<AdminSetting<?>> getHotReloadSettings() {
        List<AdminSetting<?>> hotReload = new ArrayList<>();
        for (AdminSetting<?> setting : settings.values()) {
            if (!setting.requiresRestart() && !setting.isReadOnly()) {
                hotReload.add(setting);
            }
        }
        return hotReload;
    }
    
    /**
     * Get all settings that require restart
     */
    public static List<AdminSetting<?>> getRestartRequiredSettings() {
        List<AdminSetting<?>> restartRequired = new ArrayList<>();
        for (AdminSetting<?> setting : settings.values()) {
            if (setting.requiresRestart()) {
                restartRequired.add(setting);
            }
        }
        return restartRequired;
    }
    
    /**
     * Clear all settings (for testing)
     */
    public static void clear() {
        settings.clear();
        settingsByCategory.clear();
        initialized = false;
    }
}
