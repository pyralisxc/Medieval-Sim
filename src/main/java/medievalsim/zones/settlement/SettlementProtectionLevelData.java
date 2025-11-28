/*
 * Settlement Protection Level Data for Medieval Sim Mod
 * Stores settlement protection data per level
 */
package medievalsim.zones.settlement;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.levelData.LevelData;

/**
 * Level data that stores settlement protection settings.
 * This is automatically saved/loaded with the level.
 */
public class SettlementProtectionLevelData extends LevelData {
    
    private SettlementProtectionManager manager;
    
    public SettlementProtectionLevelData() {
        // Default constructor required by LevelData
    }
    
    @Override
    public void init() {
        super.init();
        this.manager = new SettlementProtectionManager(this.getLevel());
    }
    
    /**
     * Get the settlement protection manager for this level.
     */
    public SettlementProtectionManager getManager() {
        if (this.manager == null) {
            this.manager = new SettlementProtectionManager(this.getLevel());
        }
        return this.manager;
    }
    
    @Override
    public void addSaveData(SaveData save) {
        if (this.manager != null) {
            this.manager.addSaveData(save);
        }
    }
    
    @Override
    public void applyLoadData(LoadData save) {
        if (this.manager == null) {
            this.manager = new SettlementProtectionManager(this.getLevel());
        }
        this.manager.applyLoadData(save);
    }
}

