package medievalsim.banking;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import medievalsim.util.ModLogger;
import necesse.engine.network.server.ServerClient;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;

/**
 * World-level data storage for all player banks.
 * 
 * This LevelData class persists all player bank accounts for a world.
 * Each player is identified by their authentication ID (long).
 * 
 * Thread-safety: Uses ConcurrentHashMap for thread-safe access.
 */
public class BankingLevelData extends LevelData {
    
    // Map of player auth -> PlayerBank
    private final Map<Long, PlayerBank> banks = new ConcurrentHashMap<>();
    
    // Statistics
    private long totalBanksCreated = 0;
    private long totalUpgradesPurchased = 0;
    
    /**
     * Get or create a bank for a player.
     * @param ownerAuth Player's authentication ID
     * @return PlayerBank instance (never null)
     */
    public PlayerBank getOrCreateBank(long ownerAuth) {
        return banks.computeIfAbsent(ownerAuth, auth -> {
            totalBanksCreated++;
            ModLogger.info("Created new bank for player auth=%d (total banks: %d)", auth, totalBanksCreated);
            return new PlayerBank(auth);
        });
    }
    
    /**
     * Get a bank if it exists.
     * @param ownerAuth Player's authentication ID
     * @return PlayerBank or null if doesn't exist
     */
    public PlayerBank getBank(long ownerAuth) {
        return banks.get(ownerAuth);
    }
    
    /**
     * Check if a player has a bank.
     */
    public boolean hasBank(long ownerAuth) {
        return banks.containsKey(ownerAuth);
    }
    
    /**
     * Get all banks (for admin tools, debugging).
     * @return Unmodifiable view of all banks
     */
    public Map<Long, PlayerBank> getAllBanks() {
        return new HashMap<>(banks);
    }
    
    /**
     * Record an upgrade purchase (for statistics).
     */
    public void recordUpgrade() {
        totalUpgradesPurchased++;
    }
    
    /**
     * Get total number of banks created.
     */
    public long getTotalBanksCreated() {
        return totalBanksCreated;
    }
    
    /**
     * Get total upgrades purchased across all banks.
     */
    public long getTotalUpgradesPurchased() {
        return totalUpgradesPurchased;
    }
    
    // ===== STATIC HELPER =====
    
    /**
     * Get BankingLevelData for a level, creating if necessary.
     * @param level The level
     * @return BankingLevelData instance
     */
    public static BankingLevelData getBankingData(Level level) {
        if (level == null) {
            ModLogger.error("Attempted to get BankingLevelData from null level");
            return null;
        }
        
        LevelData data = level.getLevelData("bankingdata");
        if (data instanceof BankingLevelData) {
            return (BankingLevelData) data;
        }
        
        // Create new if doesn't exist
        BankingLevelData newData = new BankingLevelData();
        level.addLevelData("bankingdata", newData);
        ModLogger.debug("Created new BankingLevelData for level %s", level.getIdentifier());
        return newData;
    }
    
    /**
     * Get a player's bank from their ServerClient.
     * @param client ServerClient
     * @return PlayerBank instance or null if error
     */
    public static PlayerBank getPlayerBank(ServerClient client) {
        if (client == null || client.playerMob == null) {
            ModLogger.error("Attempted to get bank for null client or player");
            return null;
        }

        Level level = client.playerMob.getLevel();
        if (level == null) {
            ModLogger.error("Attempted to get bank for player with null level");
            return null;
        }

        BankingLevelData bankingData = getBankingData(level);
        if (bankingData == null) {
            return null;
        }

        return bankingData.getOrCreateBank(client.authentication);
    }

    // ===== SAVE/LOAD =====

    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save);

        ModLogger.debug("Saving BankingLevelData: %d banks", banks.size());

        save.addLong("totalBanksCreated", totalBanksCreated);
        save.addLong("totalUpgradesPurchased", totalUpgradesPurchased);

        // Save all banks
        SaveData banksData = new SaveData("BANKS");
        for (PlayerBank bank : banks.values()) {
            SaveData bankSave = new SaveData("BANK");
            bank.addSaveData(bankSave);
            banksData.addSaveData(bankSave);
        }
        save.addSaveData(banksData);

        ModLogger.debug("Saved %d banks to data", banks.size());
    }

    @Override
    public void applyLoadData(LoadData save) {
        super.applyLoadData(save);

        ModLogger.debug("Loading BankingLevelData...");

        this.totalBanksCreated = save.getLong("totalBanksCreated", 0L);
        this.totalUpgradesPurchased = save.getLong("totalUpgradesPurchased", 0L);

        // Load all banks
        LoadData banksData = save.getFirstLoadDataByName("BANKS");
        if (banksData != null) {
            banks.clear();
            for (LoadData bankSave : banksData.getLoadDataByName("BANK")) {
                try {
                    long ownerAuth = bankSave.getLong("ownerAuth", 0L);
                    if (ownerAuth == 0L) {
                        ModLogger.warn("Skipping bank with invalid ownerAuth=0");
                        continue;
                    }

                    PlayerBank bank = new PlayerBank(ownerAuth);
                    bank.applyLoadData(bankSave);
                    banks.put(ownerAuth, bank);
                } catch (Exception e) {
                    ModLogger.error("Failed to load bank: %s", e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        ModLogger.info("Loaded %d banks (total created: %d, upgrades: %d)",
            banks.size(), totalBanksCreated, totalUpgradesPurchased);
    }
}

