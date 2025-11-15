package medievalsim.config;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages runtime user data (favorites, history, UI state).
 * 
 * This is separate from ModConfig (which handles configuration values)
 * because user data is:
 * - Instance-based (not static)
 * - User-specific (not global configuration)
 * - Frequently modified at runtime
 * 
 * USAGE:
 * ```java
 * // Get instance
 * SettingsManager settings = SettingsManager.getInstance();
 * 
 * // Access user data
 * settings.getFavoriteCommands().add("someCommand");
 * settings.setActiveTab(1);
 * ```
 */
public class SettingsManager {
    
    private static SettingsManager instance;
    
    // Command Center user data
    private final List<String> favoriteCommands = new ArrayList<>();
    private final List<String> commandHistory = new ArrayList<>();
    private int activeTab = 0; // 0=Console, 1=Mod Settings, 2=History
    private String lastSelectedCategory = "All";

    // Mod Settings UI state
    private final Map<String, Boolean> expandedMods = new HashMap<>(); // modId -> expanded
    private final Map<String, Boolean> expandedSections = new HashMap<>(); // "modId.sectionName" -> expanded
    
    private SettingsManager() {
        // Private constructor for singleton
    }
    
    /**
     * Get the singleton instance.
     * This is initialized by UnifiedMedievalSimSettings during mod load.
     */
    public static SettingsManager getInstance() {
        if (instance == null) {
            instance = new SettingsManager();
        }
        return instance;
    }
    
    /**
     * Reset the singleton instance (for testing or mod reload)
     */
    public static void resetInstance() {
        instance = new SettingsManager();
    }
    
    // ===== COMMAND CENTER USER DATA =====
    
    /**
     * Get the list of favorite command IDs.
     * This list can be modified directly.
     */
    public List<String> getFavoriteCommands() {
        return favoriteCommands;
    }
    
    /**
     * Get the command history list.
     * This list can be modified directly.
     */
    public List<String> getCommandHistory() {
        return commandHistory;
    }
    
    /**
     * Add a command to history (maintains max size limit)
     */
    public void addCommandToHistory(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        
        // Remove if already exists (move to front)
        commandHistory.remove(command);
        
        // Add to front
        commandHistory.add(0, command);
        
        // Trim to max size
        while (commandHistory.size() > ModConfig.CommandCenter.maxHistory) {
            commandHistory.remove(commandHistory.size() - 1);
        }
    }
    
    /**
     * Clear command history
     */
    public void clearCommandHistory() {
        commandHistory.clear();
    }
    
    /**
     * Get the active tab index (0=Console, 1=Mod Settings, 2=History)
     */
    public int getActiveTab() {
        return activeTab;
    }
    
    /**
     * Set the active tab index
     */
    public void setActiveTab(int tabIndex) {
        this.activeTab = Math.max(0, Math.min(2, tabIndex));
    }
    
    /**
     * Get the last selected command category filter
     */
    public String getLastSelectedCategory() {
        return lastSelectedCategory;
    }
    
    /**
     * Set the last selected command category filter
     */
    public void setLastSelectedCategory(String category) {
        this.lastSelectedCategory = category != null ? category : "All";
    }

    // ===== MOD SETTINGS UI STATE =====

    /**
     * Check if a mod section is expanded (default: true)
     */
    public boolean isModExpanded(String modId) {
        return expandedMods.getOrDefault(modId, true);
    }

    /**
     * Set whether a mod section is expanded
     */
    public void setModExpanded(String modId, boolean expanded) {
        expandedMods.put(modId, expanded);
    }

    /**
     * Check if a config section is expanded (default: true)
     */
    public boolean isSectionExpanded(String modId, String sectionName) {
        String key = modId + "." + sectionName;
        return expandedSections.getOrDefault(key, true);
    }

    /**
     * Set whether a config section is expanded
     */
    public void setSectionExpanded(String modId, String sectionName, boolean expanded) {
        String key = modId + "." + sectionName;
        expandedSections.put(key, expanded);
    }

    // ===== SAVE/LOAD =====
    
    /**
     * Save user data to SaveData
     */
    public void saveToData(SaveData save) {
        SaveData userData = new SaveData("USER_DATA");
        
        // Command Center data
        userData.addInt("activeTab", activeTab, "Last active tab (0=Console, 1=Mod Settings, 2=History)");
        userData.addUnsafeString("lastSelectedCategory", lastSelectedCategory, "Last selected command category filter");
        
        // Save favorites
        userData.addInt("favoriteCount", favoriteCommands.size(), "Number of favorite commands");
        for (int i = 0; i < favoriteCommands.size() && i < ModConfig.CommandCenter.maxFavorites; i++) {
            SaveData favorite = new SaveData("favorite_" + i);
            favorite.addUnsafeString("commandId", favoriteCommands.get(i));
            userData.addSaveData(favorite);
        }
        
        // Save command history
        int historyCount = Math.min(commandHistory.size(), ModConfig.CommandCenter.maxHistory);
        userData.addInt("historyCount", historyCount, "Number of history entries");
        for (int i = 0; i < historyCount; i++) {
            SaveData historyEntry = new SaveData("history_" + i);
            historyEntry.addUnsafeString("command", commandHistory.get(i));
            userData.addSaveData(historyEntry);
        }

        // Save expanded mod state
        userData.addInt("expandedModsCount", expandedMods.size());
        int modIndex = 0;
        for (Map.Entry<String, Boolean> entry : expandedMods.entrySet()) {
            SaveData modState = new SaveData("expandedMod_" + modIndex);
            modState.addUnsafeString("modId", entry.getKey());
            modState.addBoolean("expanded", entry.getValue());
            userData.addSaveData(modState);
            modIndex++;
        }

        // Save expanded section state
        userData.addInt("expandedSectionsCount", expandedSections.size());
        int sectionIndex = 0;
        for (Map.Entry<String, Boolean> entry : expandedSections.entrySet()) {
            SaveData sectionState = new SaveData("expandedSection_" + sectionIndex);
            sectionState.addUnsafeString("key", entry.getKey());
            sectionState.addBoolean("expanded", entry.getValue());
            userData.addSaveData(sectionState);
            sectionIndex++;
        }

        save.addSaveData(userData);
    }
    
    /**
     * Load user data from LoadData
     */
    public void loadFromData(LoadData save) {
        LoadData userData = save.getFirstLoadDataByName("USER_DATA");
        if (userData == null) {
            return;
        }
        
        // Load Command Center data
        activeTab = Math.max(0, Math.min(2, userData.getInt("activeTab", 0)));
        lastSelectedCategory = userData.getUnsafeString("lastSelectedCategory", "All");
        
        // Load favorites
        favoriteCommands.clear();
        int favoriteCount = userData.getInt("favoriteCount", 0);
        for (int i = 0; i < favoriteCount && i < ModConfig.CommandCenter.maxFavorites; i++) {
            LoadData favorite = userData.getFirstLoadDataByName("favorite_" + i);
            if (favorite != null) {
                String commandId = favorite.getUnsafeString("commandId", "");
                if (!commandId.isEmpty()) {
                    favoriteCommands.add(commandId);
                }
            }
        }
        
        // Load command history
        commandHistory.clear();
        int historyCount = userData.getInt("historyCount", 0);
        for (int i = 0; i < historyCount; i++) {
            LoadData historyEntry = userData.getFirstLoadDataByName("history_" + i);
            if (historyEntry != null) {
                String command = historyEntry.getUnsafeString("command", "");
                if (!command.isEmpty()) {
                    commandHistory.add(command);
                }
            }
        }

        // Load expanded mod state
        expandedMods.clear();
        int expandedModsCount = userData.getInt("expandedModsCount", 0);
        for (int i = 0; i < expandedModsCount; i++) {
            LoadData modState = userData.getFirstLoadDataByName("expandedMod_" + i);
            if (modState != null) {
                String modId = modState.getUnsafeString("modId", "");
                boolean expanded = modState.getBoolean("expanded", true);
                if (!modId.isEmpty()) {
                    expandedMods.put(modId, expanded);
                }
            }
        }

        // Load expanded section state
        expandedSections.clear();
        int expandedSectionsCount = userData.getInt("expandedSectionsCount", 0);
        for (int i = 0; i < expandedSectionsCount; i++) {
            LoadData sectionState = userData.getFirstLoadDataByName("expandedSection_" + i);
            if (sectionState != null) {
                String key = sectionState.getUnsafeString("key", "");
                boolean expanded = sectionState.getBoolean("expanded", true);
                if (!key.isEmpty()) {
                    expandedSections.put(key, expanded);
                }
            }
        }
    }
    
    /**
     * Get a summary of current settings for debugging
     */
    public String getSummary() {
        return String.format(
            "SettingsManager[activeTab=%d, category=%s, favorites=%d, history=%d]",
            activeTab, lastSelectedCategory, favoriteCommands.size(), commandHistory.size()
        );
    }
}

