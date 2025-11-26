package medievalsim.banking;

import medievalsim.config.ModConfig;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.save.levelData.InventorySave;
import necesse.inventory.Inventory;

/**
 * Represents a single player's bank account.
 * 
 * Features:
 * - PIN-protected access
 * - Upgradeable storage (20 → 120 slots)
 * - Persistent inventory
 * - Transaction tracking
 * 
 * Thread-safety: This class is NOT thread-safe. Synchronization must be handled
 * by the containing BankingLevelData class.
 */
public class PlayerBank {
    
    // Player identification
    private final long ownerAuth;

    // Security
    private int pinHash; // Hashed PIN (0 = no PIN set)
    private boolean pinSet;

    // Storage
    private int upgradeLevel; // 0-20 (configurable via ModConfig.Banking.maxUpgrades)
    private Inventory inventory;
    private long coins; // Coins stored separately (unlimited, like coin pouch)

    // Statistics
    private long totalDeposits;
    private long totalWithdrawals;
    private long totalCoinsReceived;
    private long lastAccessTime;
    private long creationTime;
    // PIN attempt tracking
    private int failedPinAttempts = 0;
    private long lockUntil = 0L; // epoch millis until which bank is locked due to failed PIN attempts
    
    /**
     * Create a new bank account for a player.
     * @param ownerAuth Player's authentication ID
     */
    public PlayerBank(long ownerAuth) {
        this(ownerAuth, 0);
    }

    /**
     * Create a new bank account for a player with a specific upgrade level.
     * Used for client-side temporary banks.
     * @param ownerAuth Player's authentication ID
     * @param upgradeLevel Initial upgrade level
     */
    public PlayerBank(long ownerAuth, int upgradeLevel) {
        this.ownerAuth = ownerAuth;
        this.pinHash = 0;
        this.pinSet = false;
        this.upgradeLevel = upgradeLevel;
        this.inventory = new Inventory(ModConfig.Banking.getTotalSlots(upgradeLevel));
        this.coins = 0L; // Start with no coins
        this.totalDeposits = 0;
        this.totalWithdrawals = 0;
        this.totalCoinsReceived = 0;
        this.creationTime = System.currentTimeMillis();
        this.lastAccessTime = this.creationTime;

        BankingDiagnostics.debug("lifecycle", "Created new bank for player auth=%d with level=%d, %d slots",
            ownerAuth, upgradeLevel, inventory.getSize());
    }
    
    // ===== GETTERS =====
    
    public long getOwnerAuth() {
        return ownerAuth;
    }
    
    public boolean isPinSet() {
        return pinSet;
    }

    /**
     * Set the client's idea of whether a PIN is set. This is used client-side only
     * to ensure the UI shows the correct 'Set PIN' or 'Change PIN' label when the
     * bank has a PIN.
     */
    public void setPinSet(boolean pinSet) {
        this.pinSet = pinSet;
    }
    
    public int getUpgradeLevel() {
        return upgradeLevel;
    }
    
    public Inventory getInventory() {
        return inventory;
    }
    
    public int getTotalSlots() {
        return inventory.getSize();
    }
    
    public long getTotalDeposits() {
        return totalDeposits;
    }
    
    public long getTotalWithdrawals() {
        return totalWithdrawals;
    }
    
    public long getTotalCoinsReceived() {
        return totalCoinsReceived;
    }
    
    public long getLastAccessTime() {
        return lastAccessTime;
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    // ===== PIN MANAGEMENT =====
    
    /**
     * Set or change the bank PIN.
     * @param pin PIN string (will be hashed)
     * @return true if PIN was set successfully
     */
    public boolean setPin(String pin) {
        if (pin == null || pin.isEmpty()) {
            BankingDiagnostics.warn("security", "Attempted to set empty PIN for auth=%d", ownerAuth);
            return false;
        }
        
        // Validate PIN length
        if (pin.length() != ModConfig.Banking.pinLength) {
            BankingDiagnostics.warn("security", "Invalid PIN length for auth=%d: expected %d, got %d", 
                ownerAuth, ModConfig.Banking.pinLength, pin.length());
            return false;
        }
        
        // Validate PIN contains only digits
        if (!pin.matches("\\d+")) {
            BankingDiagnostics.warn("security", "Invalid PIN format for auth=%d: must contain only digits", ownerAuth);
            return false;
        }
        
        this.pinHash = pin.hashCode();
        this.pinSet = true;
        BankingDiagnostics.debug("security", "PIN set for bank auth=%d", ownerAuth);
        return true;
    }
    
    /**
     * Validate a PIN attempt.
     * @param pin PIN to validate
     * @return true if PIN matches
     */
    public boolean validatePin(String pin) {
        if (!pinSet) {
            // No PIN set - allow access (first-time setup)
            return true;
        }
        
        if (pin == null) {
            return false;
        }
        
        boolean valid = pin.hashCode() == pinHash;
        if (!valid) {
            BankingDiagnostics.warn("security", "Invalid PIN attempt for bank auth=%d", ownerAuth);
        }
        return valid;
    }

    /**
     * Check if the bank is currently locked due to too many failed PIN attempts.
     */
    public boolean isLocked() {
        return System.currentTimeMillis() < lockUntil;
    }

    /**
     * Get remaining lock milliseconds, or 0 if not locked.
     */
    public long getLockRemainingMillis() {
        long now = System.currentTimeMillis();
        return Math.max(0L, lockUntil - now);
    }

    /**
     * Record a failed PIN attempt. If attempts exceed maxAttempts, lock for cooldownMillis.
     * Returns true if the bank was locked by this attempt.
     */
    public boolean recordFailedPinAttempt(int maxAttempts, long cooldownMillis) {
        failedPinAttempts++;
        if (failedPinAttempts >= maxAttempts) {
            lockUntil = System.currentTimeMillis() + cooldownMillis;
            failedPinAttempts = 0; // reset counter after locking
                BankingDiagnostics.warn("security", "Bank auth=%d locked for %d ms due to failed PIN attempts", ownerAuth, cooldownMillis);
            return true;
        }
        return false;
    }

    /**
     * Reset failed PIN attempt counter (call on successful validation or when PIN is set).
     */
    public void resetFailedPinAttempts() {
        failedPinAttempts = 0;
        lockUntil = 0L;
    }

    /**
     * Return how many attempts remain before lock occurs, given a maxAttempts limit.
     */
    public int getRemainingPinAttempts(int maxAttempts) {
        return Math.max(0, maxAttempts - failedPinAttempts);
    }

    // ===== UPGRADE MANAGEMENT =====

    /**
     * Attempt to upgrade the bank storage.
     * @return true if upgrade was successful
     */
    public boolean upgrade() {
        if (upgradeLevel >= ModConfig.Banking.maxUpgrades) {
            BankingDiagnostics.debug("upgrade", "Bank auth=%d already at max upgrade level %d", ownerAuth, upgradeLevel);
            return false;
        }

        int newUpgradeLevel = upgradeLevel + 1;
        int newTotalSlots = ModConfig.Banking.getTotalSlots(newUpgradeLevel);

        // Create new larger inventory
        Inventory newInventory = new Inventory(newTotalSlots);

        // Copy existing items
        for (int i = 0; i < inventory.getSize(); i++) {
            necesse.inventory.InventoryItem item = inventory.getItem(i);
            if (item != null) {
                newInventory.setItem(i, item);
            }
        }

        // Replace inventory
        this.inventory = newInventory;
        this.upgradeLevel = newUpgradeLevel;

        BankingDiagnostics.info("upgrade", "Upgraded bank auth=%d to level %d (%d slots)",
            ownerAuth, upgradeLevel, newTotalSlots);
        return true;
    }

    /**
     * Get the coin cost for the next upgrade.
     * @return coin cost, or -1 if already at max level
     */
    public int getNextUpgradeCost() {
        if (upgradeLevel >= ModConfig.Banking.maxUpgrades) {
            return -1;
        }
        return ModConfig.Banking.getUpgradeCost(upgradeLevel + 1);
    }

    /**
     * Check if another upgrade is available.
     */
    public boolean canUpgrade() {
        return upgradeLevel < ModConfig.Banking.maxUpgrades;
    }

    // ===== TRANSACTION TRACKING =====

    /**
     * Record a deposit transaction.
     */
    public void recordDeposit() {
        totalDeposits++;
        lastAccessTime = System.currentTimeMillis();
    }

    /**
     * Record a withdrawal transaction.
     */
    public void recordWithdrawal() {
        totalWithdrawals++;
        lastAccessTime = System.currentTimeMillis();
    }

    /**
     * Record coins received (from sales, settlements, etc.).
     * @param amount Amount of coins received
     */
    public void recordCoinsReceived(long amount) {
        totalCoinsReceived += amount;
        lastAccessTime = System.currentTimeMillis();
    }

    /**
     * Get total coins in the bank.
     * Coins are stored separately from inventory (unlimited storage).
     * @return Total number of coins
     */
    public long getCoins() {
        return coins;
    }

    /**
     * Remove coins from the bank.
     * @param amount Amount of coins to remove
     * @return true if coins were removed successfully
     */
    public boolean removeCoins(long amount) {
        if (amount <= 0) {
            return false;
        }

        // Check if bank has enough coins
        if (coins < amount) {
            BankingDiagnostics.warn("economy", "Bank auth=%d has insufficient coins: has %d, requested %d", ownerAuth, coins, amount);
            return false;
        }

        // Remove coins
        coins -= amount;
        recordWithdrawal();
        BankingDiagnostics.debug("economy", "Removed %d coins from bank auth=%d (remaining: %d)", amount, ownerAuth, coins);
        return true;
    }

    /**
     * Add coins to the bank.
     * Coins are stored separately from inventory with unlimited capacity.
     *
     * @param amount Amount of coins to add
     * @return true (always succeeds - unlimited storage)
     */
    public boolean addCoins(long amount) {
        if (amount <= 0) {
            return false;
        }

        long remainingCapacity = Long.MAX_VALUE - coins;
        if (remainingCapacity < amount) {
            coins = Long.MAX_VALUE;
        } else {
            coins += amount;
        }
        recordCoinsReceived(amount);
        BankingDiagnostics.debug("economy", "Added %d coins to bank auth=%d (total: %d)", amount, ownerAuth, coins);
        return true;
    }

    /**
     * Update last access time.
     */
    public void updateAccessTime() {
        lastAccessTime = System.currentTimeMillis();
    }

    // ===== SAVE/LOAD =====

    /**
     * Save bank data to SaveData.
     */
    public void addSaveData(SaveData save) {
        save.addLong("ownerAuth", ownerAuth);
        save.addInt("pinHash", pinHash);
        save.addBoolean("pinSet", pinSet);
        save.addInt("upgradeLevel", upgradeLevel);
        save.addLong("coins", coins); // Save coins separately

        // Save inventory using InventorySave utility
        save.addSaveData(InventorySave.getSave(inventory, "INVENTORY"));

        // Save statistics
        save.addLong("totalDeposits", totalDeposits);
        save.addLong("totalWithdrawals", totalWithdrawals);
        save.addLong("totalCoinsReceived", totalCoinsReceived);
        save.addLong("lastAccessTime", lastAccessTime);
        save.addLong("creationTime", creationTime);
    }

    /**
     * Load bank data from LoadData.
     */
    public void applyLoadData(LoadData save) {
        this.pinHash = save.getInt("pinHash", 0);
        this.pinSet = save.getBoolean("pinSet", false);
        this.upgradeLevel = save.getInt("upgradeLevel", 0);
        this.coins = save.getLong("coins", 0L); // Load coins separately

        // Load inventory using InventorySave utility
        LoadData inventoryLoad = save.getFirstLoadDataByName("INVENTORY");
        if (inventoryLoad != null) {
            Inventory loadedInventory = InventorySave.loadSave(inventoryLoad);
            int expectedSize = ModConfig.Banking.getTotalSlots(upgradeLevel);
            this.inventory = new Inventory(expectedSize);
            this.inventory.override(loadedInventory, false, false);
        } else {
            // Fallback: create empty inventory
            this.inventory = new Inventory(ModConfig.Banking.getTotalSlots(upgradeLevel));
        }

        // Load statistics
        this.totalDeposits = save.getLong("totalDeposits", 0L);
        this.totalWithdrawals = save.getLong("totalWithdrawals", 0L);
        this.totalCoinsReceived = save.getLong("totalCoinsReceived", 0L);
        this.lastAccessTime = save.getLong("lastAccessTime", System.currentTimeMillis());
        this.creationTime = save.getLong("creationTime", System.currentTimeMillis());

        BankingDiagnostics.debug("persistence", "Loaded bank auth=%d: level=%d, slots=%d, coins=%d, deposits=%d, withdrawals=%d",
            ownerAuth, upgradeLevel, inventory.getSize(), coins, totalDeposits, totalWithdrawals);
    }
}
