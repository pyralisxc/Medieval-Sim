/*
 * Settlement Protection Manager for Medieval Sim Mod
 * Manages protection data for all settlements on a level
 */
package medievalsim.zones;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;
import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;

/**
 * Manages settlement protection data for a level.
 * Stores protection settings per settlement (keyed by settlement tile position).
 */
public class SettlementProtectionManager {
    
    // Map of settlement position -> protection data
    private final Map<Point, SettlementProtectionData> protectionMap = new HashMap<>();
    @SuppressWarnings("unused")
    private final Level level; // Reserved for future level-based hooks and validation
    
    public SettlementProtectionManager(Level level) {
        this.level = level;
    }
    
    /**
     * Get protection data for a settlement at the given position.
     * Creates new data if it doesn't exist.
     */
    public SettlementProtectionData getProtectionData(int settlementTileX, int settlementTileY) {
        Point key = new Point(settlementTileX, settlementTileY);
        return protectionMap.computeIfAbsent(key, k -> new SettlementProtectionData());
    }
    
    /**
     * Check if a settlement has protection enabled.
     */
    public boolean isProtectionEnabled(int settlementTileX, int settlementTileY) {
        if (!ModConfig.Settlements.protectionEnabled) {
            return false; // Global toggle disabled
        }
        
        SettlementProtectionData data = getProtectionData(settlementTileX, settlementTileY);
        return data.isEnabled();
    }
    
    /**
     * Enable or disable protection for a settlement.
     */
    public void setProtectionEnabled(int settlementTileX, int settlementTileY, boolean enabled) {
        SettlementProtectionData data = getProtectionData(settlementTileX, settlementTileY);
        data.setEnabled(enabled);
    }
    
    /**
     * Remove protection data for a settlement (called when settlement is removed).
     */
    public void removeProtection(int settlementTileX, int settlementTileY) {
        Point key = new Point(settlementTileX, settlementTileY);
        protectionMap.remove(key);
        ModLogger.debug("Removed settlement protection data at (%d, %d)", settlementTileX, settlementTileY);
    }
    
    /**
     * Clear all protection data.
     */
    public void clear() {
        protectionMap.clear();
    }
    
    // ===== SAVE/LOAD =====
    
    public void addSaveData(SaveData save) {
        SaveData protectionSave = new SaveData("settlementProtections");
        
        for (Map.Entry<Point, SettlementProtectionData> entry : protectionMap.entrySet()) {
            Point pos = entry.getKey();
            SettlementProtectionData data = entry.getValue();
            
            SaveData settlementSave = new SaveData("protection");
            settlementSave.addInt("tileX", pos.x);
            settlementSave.addInt("tileY", pos.y);
            data.addSaveData(settlementSave);
            
            protectionSave.addSaveData(settlementSave);
        }
        
        save.addSaveData(protectionSave);
        ModLogger.debug("Saved %d settlement protection entries", protectionMap.size());
    }
    
    public void applyLoadData(LoadData save) {
        protectionMap.clear();
        
        LoadData protectionSave = save.getFirstLoadDataByName("settlementProtections");
        if (protectionSave != null) {
            for (LoadData settlementSave : protectionSave.getLoadDataByName("protection")) {
                try {
                    int tileX = settlementSave.getInt("tileX");
                    int tileY = settlementSave.getInt("tileY");
                    
                    SettlementProtectionData data = new SettlementProtectionData();
                    data.applyLoadData(settlementSave);
                    
                    protectionMap.put(new Point(tileX, tileY), data);
                } catch (Exception e) {
                    ModLogger.error("Failed to load settlement protection data: %s", e.getMessage());
                }
            }
            ModLogger.debug("Loaded %d settlement protection entries", protectionMap.size());
        }
    }
    
    /**
     * Get the number of protected settlements.
     */
    public int getProtectedSettlementCount() {
        return (int) protectionMap.values().stream()
            .filter(SettlementProtectionData::isEnabled)
            .count();
    }
}

