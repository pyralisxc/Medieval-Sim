package medievalsim.banking.domain;

import medievalsim.banking.diagnostics.BankingDiagnostics;
import medievalsim.config.ModConfig;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.save.levelData.InventorySave;
import necesse.inventory.Inventory;

public class PlayerBank {

    private final long ownerAuth;
    private int pinHash;
    private boolean pinSet;
    private int upgradeLevel;
    private Inventory inventory;
    private long coins;
    private long totalDeposits;
    private long totalWithdrawals;
    private long totalCoinsReceived;
    private long lastAccessTime;
    private long creationTime;
    private int failedPinAttempts = 0;
    private long lockUntil = 0L;

    public PlayerBank(long ownerAuth) {
        this(ownerAuth, 0);
    }

    public PlayerBank(long ownerAuth, int upgradeLevel) {
        this.ownerAuth = ownerAuth;
        this.pinHash = 0;
        this.pinSet = false;
        this.upgradeLevel = upgradeLevel;
        this.inventory = new Inventory(ModConfig.Banking.getTotalSlots(upgradeLevel));
        this.coins = 0L;
        this.creationTime = System.currentTimeMillis();
        this.lastAccessTime = this.creationTime;

        BankingDiagnostics.debug("lifecycle", "Created new bank for player auth=%d with level=%d, %d slots",
            ownerAuth, upgradeLevel, inventory.getSize());
    }

    public long getOwnerAuth() {
        return ownerAuth;
    }

    public boolean isPinSet() {
        return pinSet;
    }

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

    public boolean setPin(String pin) {
        if (pin == null || pin.isEmpty()) {
            BankingDiagnostics.warn("security", "Attempted to set empty PIN for auth=%d", ownerAuth);
            return false;
        }
        if (pin.length() != ModConfig.Banking.pinLength) {
            BankingDiagnostics.warn("security", "Invalid PIN length for auth=%d: expected %d, got %d",
                ownerAuth, ModConfig.Banking.pinLength, pin.length());
            return false;
        }
        if (!pin.matches("\\d+")) {
            BankingDiagnostics.warn("security", "Invalid PIN format for auth=%d: must contain only digits", ownerAuth);
            return false;
        }
        this.pinHash = pin.hashCode();
        this.pinSet = true;
        BankingDiagnostics.debug("security", "PIN set for bank auth=%d", ownerAuth);
        return true;
    }

    public boolean validatePin(String pin) {
        if (!pinSet) {
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

    public boolean isLocked() {
        return System.currentTimeMillis() < lockUntil;
    }

    public long getLockRemainingMillis() {
        long now = System.currentTimeMillis();
        return Math.max(0L, lockUntil - now);
    }

    public boolean recordFailedPinAttempt(int maxAttempts, long cooldownMillis) {
        failedPinAttempts++;
        if (failedPinAttempts >= maxAttempts) {
            lockUntil = System.currentTimeMillis() + cooldownMillis;
            failedPinAttempts = 0;
            BankingDiagnostics.warn("security", "Bank auth=%d locked for %d ms due to failed PIN attempts", ownerAuth, cooldownMillis);
            return true;
        }
        return false;
    }

    public void resetFailedPinAttempts() {
        failedPinAttempts = 0;
        lockUntil = 0L;
    }

    public int getRemainingPinAttempts(int maxAttempts) {
        return Math.max(0, maxAttempts - failedPinAttempts);
    }

    public boolean upgrade() {
        if (upgradeLevel >= ModConfig.Banking.maxUpgrades) {
            BankingDiagnostics.debug("upgrade", "Bank auth=%d already at max upgrade level %d", ownerAuth, upgradeLevel);
            return false;
        }

        int newUpgradeLevel = upgradeLevel + 1;
        int newTotalSlots = ModConfig.Banking.getTotalSlots(newUpgradeLevel);
        Inventory newInventory = new Inventory(newTotalSlots);

        for (int i = 0; i < inventory.getSize(); i++) {
            necesse.inventory.InventoryItem item = inventory.getItem(i);
            if (item != null) {
                newInventory.setItem(i, item);
            }
        }

        this.inventory = newInventory;
        this.upgradeLevel = newUpgradeLevel;

        BankingDiagnostics.info("upgrade", "Upgraded bank auth=%d to level %d (%d slots)",
            ownerAuth, upgradeLevel, newTotalSlots);
        return true;
    }

    public int getNextUpgradeCost() {
        if (upgradeLevel >= ModConfig.Banking.maxUpgrades) {
            return -1;
        }
        return ModConfig.Banking.getUpgradeCost(upgradeLevel + 1);
    }

    public boolean canUpgrade() {
        return upgradeLevel < ModConfig.Banking.maxUpgrades;
    }

    public void recordDeposit() {
        totalDeposits++;
        lastAccessTime = System.currentTimeMillis();
    }

    public void recordWithdrawal() {
        totalWithdrawals++;
        lastAccessTime = System.currentTimeMillis();
    }

    public void recordCoinsReceived(long amount) {
        totalCoinsReceived += amount;
        lastAccessTime = System.currentTimeMillis();
    }

    public long getCoins() {
        return coins;
    }

    public boolean removeCoins(long amount) {
        if (amount <= 0) {
            return false;
        }
        if (coins < amount) {
            BankingDiagnostics.warn("economy", "Bank auth=%d has insufficient coins: has %d, requested %d", ownerAuth, coins, amount);
            return false;
        }
        coins -= amount;
        recordWithdrawal();
        BankingDiagnostics.debug("economy", "Removed %d coins from bank auth=%d (remaining: %d)", amount, ownerAuth, coins);
        return true;
    }

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

    public void updateAccessTime() {
        lastAccessTime = System.currentTimeMillis();
    }
    
    /**
     * Set coins directly (for rollback operations only).
     * WARNING: Use with caution - bypasses normal coin tracking.
     */
    public void setCoins(long amount) {
        this.coins = amount;
        updateAccessTime();
    }

    public void addSaveData(SaveData save) {
        save.addLong("ownerAuth", ownerAuth);
        save.addInt("pinHash", pinHash);
        save.addBoolean("pinSet", pinSet);
        save.addInt("upgradeLevel", upgradeLevel);
        save.addLong("coins", coins);
        save.addSaveData(InventorySave.getSave(inventory, "INVENTORY"));
        save.addLong("totalDeposits", totalDeposits);
        save.addLong("totalWithdrawals", totalWithdrawals);
        save.addLong("totalCoinsReceived", totalCoinsReceived);
        save.addLong("lastAccessTime", lastAccessTime);
        save.addLong("creationTime", creationTime);
    }

    public void applyLoadData(LoadData save) {
        this.pinHash = save.getInt("pinHash", 0);
        this.pinSet = save.getBoolean("pinSet", false);
        this.upgradeLevel = save.getInt("upgradeLevel", 0);
        this.coins = save.getLong("coins", 0L);

        LoadData inventoryLoad = save.getFirstLoadDataByName("INVENTORY");
        if (inventoryLoad != null) {
            Inventory loadedInventory = InventorySave.loadSave(inventoryLoad);
            int expectedSize = ModConfig.Banking.getTotalSlots(upgradeLevel);
            this.inventory = new Inventory(expectedSize);
            this.inventory.override(loadedInventory, false, false);
        } else {
            this.inventory = new Inventory(ModConfig.Banking.getTotalSlots(upgradeLevel));
        }

        this.totalDeposits = save.getLong("totalDeposits", 0L);
        this.totalWithdrawals = save.getLong("totalWithdrawals", 0L);
        this.totalCoinsReceived = save.getLong("totalCoinsReceived", 0L);
        this.lastAccessTime = save.getLong("lastAccessTime", System.currentTimeMillis());
        this.creationTime = save.getLong("creationTime", System.currentTimeMillis());

        BankingDiagnostics.debug("persistence", "Loaded bank auth=%d: level=%d, slots=%d, coins=%d, deposits=%d, withdrawals=%d",
            ownerAuth, upgradeLevel, inventory.getSize(), coins, totalDeposits, totalWithdrawals);
    }
}
