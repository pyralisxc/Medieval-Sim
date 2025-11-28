package medievalsim.banking.service;

import medievalsim.banking.diagnostics.BankingDiagnostics;
import medievalsim.banking.domain.BankingLevelData;
import medievalsim.banking.domain.PlayerBank;
import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;

/**
 * Server-side service that centralizes all banking business logic so
 * containers and packets can simply delegate intent-level operations.
 */
public class BankingService {
    private static final int MAX_PIN_ATTEMPTS = 3;
    private static final long PIN_LOCK_COOLDOWN_MILLIS = 5 * 60 * 1000L;

    private final ServerClient client;
    private final PlayerBank bank;

    public BankingService(ServerClient client) {
        this.client = client;
        BankingLevelData data = BankingLevelData.getBankingData(client.playerMob.getLevel());
        this.bank = data != null ? data.getOrCreateBank(client.authentication) : null;
    }

    public PlayerBank getBank() {
        return bank;
    }

    public BankingResult depositCoins(int amount) {
        if (bank == null) {
            return BankingResult.failure(BankingResult.Status.ERROR, "bankerror");
        }
        if (amount <= 0) {
            return BankingResult.failure(BankingResult.Status.INVALID_AMOUNT, "bankdepositinvalid");
        }

        Item coinItem = ItemRegistry.getItem("coin");
        if (coinItem == null) {
            ModLogger.error("Coin item missing during deposit");
            return BankingResult.failure(BankingResult.Status.ERROR, "bankerror");
        }

        int playerCoins = client.playerMob.getInv().main.getAmount(client.playerMob.getLevel(), client.playerMob, coinItem, "deposit");
        int actualAmount = Math.min(amount, playerCoins);
        if (actualAmount <= 0) {
            return BankingResult.failure(BankingResult.Status.INSUFFICIENT_COINS, "bankdepositnone");
        }

        int removed = client.playerMob.getInv().main.removeItems(client.playerMob.getLevel(), client.playerMob, coinItem, actualAmount, "deposit");
        if (removed > 0) {
            bank.addCoins(removed);
            BankingDiagnostics.info("economy", "Player auth=%d deposited %d", client.authentication, removed);
            return BankingResult.success(removed);
        }
        return BankingResult.failure(BankingResult.Status.ERROR, "bankerror");
    }

    public BankingResult withdrawCoins(int amount) {
        if (bank == null) {
            return BankingResult.failure(BankingResult.Status.ERROR, "bankerror");
        }
        if (amount <= 0) {
            return BankingResult.failure(BankingResult.Status.INVALID_AMOUNT, "bankwithdrawinvalid");
        }
        long bankCoins = bank.getCoins();
        long request = Math.min((long) amount, bankCoins);
        if (request <= 0) {
            return BankingResult.failure(BankingResult.Status.INSUFFICIENT_COINS, "bankemptynocoins");
        }

        Item coinItem = ItemRegistry.getItem("coin");
        if (coinItem == null) {
            return BankingResult.failure(BankingResult.Status.ERROR, "bankerror");
        }

        long remaining = request;
        long delivered = 0;
        while (remaining > 0) {
            int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
            InventoryItem stack = new InventoryItem(coinItem, chunk);
            client.playerMob.getInv().main.addItem(client.playerMob.getLevel(), client.playerMob, stack, "withdrawal", null);
            int leftover = stack.getAmount();
            int inserted = chunk - leftover;
            if (inserted <= 0) {
                break;
            }
            delivered += inserted;
            remaining -= inserted;
            if (leftover > 0) {
                break;
            }
        }

        if (delivered == 0) {
            return BankingResult.failure(BankingResult.Status.INVENTORY_FULL, "bankinventoryfull");
        }

        if (!bank.removeCoins(delivered)) {
            return BankingResult.failure(BankingResult.Status.ERROR, "bankerror");
        }

        if (remaining > 0) {
            return BankingResult.partial((int) delivered, remaining, "bankwithdrawpartial");
        }
        return BankingResult.success((int) delivered);
    }

    public boolean upgradeBank() {
        if (bank == null || !bank.canUpgrade()) {
            return false;
        }
        int cost = bank.getNextUpgradeCost();
        if (cost <= 0) {
            return false;
        }
        Item coinItem = ItemRegistry.getItem("coin");
        if (coinItem == null) {
            return false;
        }
        int playerCoins = client.playerMob.getInv().main.getAmount(client.playerMob.getLevel(), client.playerMob, coinItem, "upgrade");
        if (playerCoins < cost) {
            return false;
        }
        int removed = client.playerMob.getInv().main.removeItems(client.playerMob.getLevel(), client.playerMob, coinItem, cost, "upgrade");
        if (removed != cost) {
            return false;
        }
        if (bank.upgrade()) {
            ModLogger.info("Player auth=%d upgraded bank to level %d", client.authentication, bank.getUpgradeLevel());
            BankingLevelData bankingData = BankingLevelData.getBankingData(client.playerMob.getLevel());
            if (bankingData != null) {
                bankingData.recordUpgrade();
            }
            return true;
        }
        return false;
    }

    public boolean validatePin(String pin) {
        if (bank == null) {
            return false;
        }
        if (bank.isLocked()) {
            long remaining = bank.getLockRemainingMillis();
            client.sendChatMessage(Localization.translate("ui", "bankpinlocked", "seconds", String.valueOf(remaining / 1000)));
            return false;
        }
        boolean valid = bank.validatePin(pin);
        if (!valid) {
            boolean locked = bank.recordFailedPinAttempt(MAX_PIN_ATTEMPTS, PIN_LOCK_COOLDOWN_MILLIS);
            if (locked) {
                client.sendChatMessage(Localization.translate("ui", "bankpinlocked", "seconds", String.valueOf(PIN_LOCK_COOLDOWN_MILLIS / 1000)));
            } else {
                client.sendChatMessage(Localization.translate("ui", "bankpinfailed", "attempts", String.valueOf(bank.getRemainingPinAttempts(MAX_PIN_ATTEMPTS))));
            }
            return false;
        }
        bank.resetFailedPinAttempts();
        return true;
    }

    public boolean setPin(String pin) {
        if (bank == null) {
            return false;
        }
        if (bank.setPin(pin)) {
            bank.resetFailedPinAttempts();
            return true;
        }
        return false;
    }

}
