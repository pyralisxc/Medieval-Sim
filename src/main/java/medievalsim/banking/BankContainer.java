package medievalsim.banking;

import medievalsim.config.ModConfig;
import medievalsim.packets.PacketBankInventoryUpdate;
import medievalsim.registries.MedievalSimContainers;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.container.Container;
import necesse.inventory.container.customAction.BooleanCustomAction;
import necesse.inventory.container.customAction.EmptyCustomAction;
import necesse.inventory.container.customAction.IntCustomAction;
import necesse.inventory.container.customAction.LongCustomAction;
import necesse.inventory.container.customAction.StringCustomAction;
import necesse.inventory.container.slots.ContainerSlot;
import necesse.inventory.item.Item;
import necesse.inventory.InventoryItem;
import necesse.engine.localization.Localization;

/**
 * Server and client-side container for player banks.
 * 
 * Manages bank inventory slots, upgrades, and PIN validation.
 */
public class BankContainer extends Container {
    
    // Bank data
    public final PlayerBank bank;
    public final long ownerAuth;
    
    // Slot indices
    public int BANK_INVENTORY_START = -1;
    public int BANK_INVENTORY_END = -1;
    
    // Container state
    public boolean isPinValidated = false;
    public int currentUpgradeLevel = 0;
    public int maxUpgradeLevel = ModConfig.Banking.maxUpgrades;
    public long clientCoinCount = 0; // Client-side coin count (synced from server)
    public long lastSyncedCoinCount = 0; // Server-side tracker for last synced coin count
    
    // Custom actions
    public EmptyCustomAction purchaseUpgrade;
    public StringCustomAction validatePin;
    public StringCustomAction setNewPin;
    public BooleanCustomAction setPinValidated;
    public BooleanCustomAction setBankPinSet; // Sync pinSet state to client
    public IntCustomAction depositCoins;
    public IntCustomAction withdrawCoins;
    public LongCustomAction syncCoinCount; // Sync coin count from server to client

    // Inventory management actions
    public EmptyCustomAction sortButton;
    public EmptyCustomAction lootButton;
    public EmptyCustomAction quickStackButton;

    /**
     * Constructor for both client and server.
     * @param client NetworkClient
     * @param uniqueSeed Unique container seed
     * @param content Packet content with bank data
     */
    public BankContainer(NetworkClient client, int uniqueSeed, Packet content) {
        super(client, uniqueSeed);

        PacketReader reader = new PacketReader(content);
        this.ownerAuth = reader.getNextLong();
        this.currentUpgradeLevel = reader.getNextInt();
        this.isPinValidated = reader.getNextBoolean();
        boolean pinSet = reader.getNextBoolean(); // Whether the bank has a PIN set (client-side only)
        this.clientCoinCount = reader.getNextLong(); // Read coin count

        // CRITICAL: Both client and server must reference the SAME bank object
        // Server-side: Get the actual bank from level data
        // Client-side: The bank reference will be set by applyTempInventoryPacket
        if (client.isServer()) {
            this.bank = BankingLevelData.getPlayerBank(client.getServerClient());
            if (this.bank == null) {
                ModLogger.error("Failed to get bank for player auth=%d", ownerAuth);
                throw new IllegalStateException("Bank not found for player");
            }
            this.clientCoinCount = bank.getCoins(); // Sync server coin count
            this.lastSyncedCoinCount = bank.getCoins();
            ModLogger.debug("Server: Loaded bank for auth=%d, level=%d, slots=%d, coins=%d",
                ownerAuth, bank.getUpgradeLevel(), bank.getInventory().getSize(), bank.getCoins());
        } else {
            // Client-side: Create a temporary bank that will be populated from packet data
            this.bank = new PlayerBank(ownerAuth, currentUpgradeLevel);
        }
        if (!client.isServer()) {
            this.bank.setPinSet(pinSet);
        }
        int totalSlots = ModConfig.Banking.getTotalSlots(currentUpgradeLevel);
        
        for (int i = 0; i < totalSlots; i++) {
            int index = this.addSlot(new ContainerSlot(bank.getInventory(), i));
            
            if (BANK_INVENTORY_START == -1) {
                BANK_INVENTORY_START = index;
            }
            BANK_INVENTORY_END = index;
        }
        
        this.setupQuickTransfer();
        
        ModLogger.debug("Added %d bank slots (indices %d-%d)", 
            totalSlots, BANK_INVENTORY_START, BANK_INVENTORY_END);

        registerActions();
    }
    
    /**
     * Register custom actions for container.
     */
    private void registerActions() {
        // Purchase upgrade action
        this.purchaseUpgrade = this.registerAction(new EmptyCustomAction() {
            @Override
            protected void run() {
                if (client.isServer()) {
                    handlePurchaseUpgrade();
                }
            }
        });
        
        // Validate PIN action
        this.validatePin = this.registerAction(new StringCustomAction() {
            @Override
            protected void run(String pin) {
                if (client.isServer()) {
                    handleValidatePin(pin);
                }
            }
        });

        // Set new PIN action
        this.setNewPin = this.registerAction(new StringCustomAction() {
            @Override
            protected void run(String pin) {
                if (client.isServer()) {
                    handleSetNewPin(pin);
                }
            }
        });
        
        // Set PIN validated state (server -> client sync)
        this.setPinValidated = this.registerAction(new BooleanCustomAction() {
            @Override
            protected void run(boolean validated) {
                isPinValidated = validated;
            }
        });

        // Set bank PIN state (server -> client sync)
        this.setBankPinSet = this.registerAction(new BooleanCustomAction() {
            @Override
            protected void run(boolean pinSet) {
                if (!client.isServer()) {
                    bank.setPinSet(pinSet);
                }
            }
        });

        // Deposit coins action
        this.depositCoins = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int amount) {
                if (client.isServer()) {
                    handleDepositCoins(amount);
                }
            }
        });

        // Withdraw coins action
        this.withdrawCoins = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int amount) {
                if (client.isServer()) {
                    handleWithdrawCoins(amount);
                }
            }
        });

        // Sync coin count action (server sends to client)
        this.syncCoinCount = this.registerAction(new LongCustomAction() {
            @Override
            protected void run(long coinCount) {
                if (!client.isServer()) {
                    clientCoinCount = coinCount;
                    ModLogger.debug("Client: Updated coin count to %d", coinCount);
                }
            }
        });

        // Sort inventory action
        this.sortButton = this.registerAction(new EmptyCustomAction() {
            @Override
            protected void run() {
                if (client.isServer()) {
                    bank.getInventory().sortItems(
                        client.playerMob.getLevel(),
                        client.playerMob,
                        0,
                        bank.getInventory().getSize() - 1
                    );
                    // Mark all slots as dirty so they sync to client
                    bank.getInventory().markFullDirty();
                    ModLogger.debug("Sorted bank inventory for player auth=%d", ownerAuth);
                }
            }
        });

        // Loot all action - transfer all items from bank to player inventory
        this.lootButton = this.registerAction(new EmptyCustomAction() {
            @Override
            protected void run() {
                if (client.isServer()) {
                    for (int i = BANK_INVENTORY_START; i <= BANK_INVENTORY_END; i++) {
                        if (getSlot(i).isItemLocked()) continue;
                        transferToSlots(getSlot(i), CLIENT_HOTBAR_START, CLIENT_INVENTORY_END, "lootall");
                    }
                    ModLogger.debug("Looted all items from bank for player auth=%d", ownerAuth);
                }
            }
        });

        // Quick stack action - transfer matching items from player inventory to bank
        this.quickStackButton = this.registerAction(new EmptyCustomAction() {
            @Override
            protected void run() {
                if (client.isServer()) {
                    for (int i = CLIENT_INVENTORY_START; i <= CLIENT_INVENTORY_END; i++) {
                        if (getSlot(i).isItemLocked() || getSlot(i).isClear()) continue;

                        // Check if bank already has this item type
                        if (bank.getInventory().getAmount(
                            client.playerMob.getLevel(),
                            client.playerMob,
                            getSlot(i).getItem().item,
                            0,
                            bank.getInventory().getSize() - 1,
                            "quickstack") > 0) {

                            // Transfer to bank
                            transferToSlots(getSlot(i), BANK_INVENTORY_START, BANK_INVENTORY_END, "quickstack");
                        }
                    }
                    ModLogger.debug("Quick stacked items to bank for player auth=%d", ownerAuth);
                }
            }
        });
    }
    
    /**
     * Setup quick transfer options between player inventory and bank.
     */
    private void setupQuickTransfer() {
        // Player inventory -> Bank
        this.addInventoryQuickTransfer(
            BANK_INVENTORY_START,
            BANK_INVENTORY_END
        );
    }

    // ===== KEYBOARD SHORTCUT OVERRIDES =====

    @Override
    public void lootAllControlPressed() {
        this.lootButton.runAndSend();
    }

    @Override
    public void sortInventoryControlPressed() {
        this.sortButton.runAndSend();
    }

    @Override
    public void quickStackControlPressed() {
        this.quickStackButton.runAndSend();
    }

    // ===== SERVER-SIDE HANDLERS =====

    /**
     * Handle upgrade purchase (server-side).
     */
    private void handlePurchaseUpgrade() {
        if (!canUpgrade()) {
            ModLogger.warn("Player auth=%d attempted invalid upgrade", ownerAuth);
            return;
        }

        int cost = bank.getNextUpgradeCost();

        // Get coin item
        Item coinItem = ItemRegistry.getItem("coin");
        if (coinItem == null) {
            ModLogger.error("Coin item not found in registry!");
            return;
        }

        // Check if player has enough coins
        int playerCoins = client.playerMob.getInv().main.getAmount(
            client.playerMob.getLevel(), client.playerMob, coinItem, "upgrade");
        if (playerCoins < cost) {
            ModLogger.debug("Player auth=%d has insufficient coins for upgrade (%d < %d)",
                ownerAuth, playerCoins, cost);
            return;
        }

        // Remove coins from player
        int removed = client.playerMob.getInv().main.removeItems(
            client.playerMob.getLevel(), client.playerMob, coinItem, cost, "upgrade");
        if (removed != cost) {
            ModLogger.error("Failed to remove coins from player auth=%d (removed %d, expected %d)",
                ownerAuth, removed, cost);
            return;
        }

        // Perform upgrade
        if (bank.upgrade()) {
            currentUpgradeLevel = bank.getUpgradeLevel();

            // Record in level data
            BankingLevelData bankingData = BankingLevelData.getBankingData(client.playerMob.getLevel());
            if (bankingData != null) {
                bankingData.recordUpgrade();
            }

            ModLogger.info("Player auth=%d upgraded bank to level %d (cost: %d coins)",
                ownerAuth, currentUpgradeLevel, cost);

            // Reopen container to refresh slots with new upgrade level
            ServerClient serverClient = client.getServerClient();
            serverClient.closeContainer(false);  // Close without sending close packet

            // Reopen with updated upgrade level
            Packet containerPacket = BankContainer.getOpenPacketContent(
                serverClient.authentication,
                bank.getUpgradeLevel(),
                isPinValidated,
                bank
            );

            PacketOpenContainer openPacket = new PacketOpenContainer(
                MedievalSimContainers.BANK_CONTAINER,
                containerPacket
            );
            ContainerRegistry.openAndSendContainer(serverClient, openPacket);

            ModLogger.debug("Reopened bank after upgrade for player auth=%d", ownerAuth);
        } else {
            ModLogger.error("Bank upgrade failed for player auth=%d", ownerAuth);
        }
    }

    /**
     * Handle PIN validation (server-side).
     */
    private void handleValidatePin(String pin) {
        if (bank.validatePin(pin)) {
            isPinValidated = true;
            setPinValidated.runAndSend(true);
            ModLogger.debug("PIN validated for player auth=%d", ownerAuth);
        } else {
            isPinValidated = false;
            setPinValidated.runAndSend(false);
            ModLogger.debug("PIN validation failed for player auth=%d", ownerAuth);
        }
    }

    /**
     * Handle setting new PIN (server-side).
     */
    private void handleSetNewPin(String pin) {
        bank.setPin(pin);
        isPinValidated = true;
        setPinValidated.runAndSend(true);
        // Inform client that a PIN is now set on this bank
        setBankPinSet.runAndSend(true);
        ModLogger.info("New PIN set for player auth=%d", ownerAuth);
    }

    /**
     * Handle coin deposit (server-side).
     */
    private void handleDepositCoins(int amount) {
        if (amount <= 0) {
            ModLogger.warn("Player auth=%d attempted to deposit invalid amount: %d", ownerAuth, amount);
            return;
        }

        // Get coin item
        Item coinItem = ItemRegistry.getItem("coin");
        if (coinItem == null) {
            ModLogger.error("Coin item not found in registry!");
            return;
        }

        // Check if player has enough coins
        int playerCoins = client.playerMob.getInv().main.getAmount(
            client.playerMob.getLevel(), client.playerMob, coinItem, "deposit");

        int actualAmount = Math.min(amount, playerCoins);
        if (actualAmount <= 0) {
            ModLogger.debug("Player auth=%d has no coins to deposit", ownerAuth);
            return;
        }

        // Remove coins from player inventory
        int removed = client.playerMob.getInv().main.removeItems(
            client.playerMob.getLevel(), client.playerMob, coinItem, actualAmount, "deposit");

        if (removed > 0) {
            // Add coins to bank
            bank.addCoins(removed);
            ModLogger.info("Player auth=%d deposited %d coins to bank", ownerAuth, removed);

            // Sync coin count to client
            syncCoinCount.runAndSend(bank.getCoins());
        }
    }

    /**
     * Handle coin withdrawal (server-side).
     */
    private void handleWithdrawCoins(int amount) {
        if (amount <= 0) {
            ModLogger.warn("Player auth=%d attempted to withdraw invalid amount: %d", ownerAuth, amount);
            return;
        }
        long bankCoins = bank.getCoins();
        long request = Math.min((long) amount, bankCoins);

        if (request <= 0) {
            ModLogger.debug("Bank for player auth=%d has no coins to withdraw", ownerAuth);
            return;
        }

        Item coinItem = ItemRegistry.getItem("coin");
        if (coinItem == null) {
            ModLogger.error("Coin item not found in registry!");
            return;
        }

        long remaining = request;
        long delivered = 0;

        while (remaining > 0) {
            int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
            InventoryItem coinStack = new InventoryItem(coinItem, chunk);
            client.playerMob.getInv().main.addItem(
                client.playerMob.getLevel(),
                client.playerMob,
                coinStack,
                "withdrawal",
                null
            );

            int leftover = coinStack.getAmount();
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
            sendBankMessage("bankinventoryfull");
            ModLogger.debug("Player auth=%d could not receive coins because inventory is full", ownerAuth);
            return;
        }

        boolean coinsChanged = false;
        if (bank.removeCoins(delivered)) {
            coinsChanged = true;
            ModLogger.info("Player auth=%d withdrew %d coins from bank", ownerAuth, delivered);
        } else {
            ModLogger.error("Failed to remove withdrawn coins for auth=%d", ownerAuth);
            delivered = 0;
        }

        if (remaining > 0 && coinsChanged) {
            sendBankMessage("bankwithdrawpartial", "delivered", String.valueOf(delivered), "remaining", String.valueOf(remaining));
            ModLogger.debug("Player auth=%d withdrew %d but %d coins refunded due to limited space", ownerAuth, delivered, remaining);
        }

        if (coinsChanged) {
            syncCoinCount.runAndSend(bank.getCoins());
        }
    }

    // ===== HELPER METHODS =====

    /**
     * Check if bank can be upgraded.
     */
    public boolean canUpgrade() {
        return bank != null && bank.canUpgrade();
    }

    /**
     * Get cost of next upgrade.
     */
    public int getNextUpgradeCost() {
        return bank != null ? bank.getNextUpgradeCost() : 0;
    }

    /**
     * Get current number of bank slots.
     */
    public int getCurrentSlots() {
        return ModConfig.Banking.getTotalSlots(currentUpgradeLevel);
    }

    /**
     * Get maximum possible slots.
     */
    public int getMaxSlots() {
        return ModConfig.Banking.getTotalSlots(maxUpgradeLevel);
    }

    /**
     * Get the bank instance.
     */
    public PlayerBank getBank() {
        return this.bank;
    }

    private void sendBankMessage(String key, Object... args) {
        if (!client.isServer()) {
            return;
        }
        ServerClient serverClient = client.getServerClient();
        if (serverClient == null) {
            return;
        }
        serverClient.sendChatMessage(Localization.translate("ui", key, args));
    }

    @Override
    public void tick() {
        super.tick();

        // Server-side: Sync dirty inventory slots to client
        // Similar to OEInventory.serverTickInventorySync()
        if (client.isServer()) {
            necesse.inventory.Inventory inventory = bank.getInventory();

            if (inventory.isDirty()) {
                necesse.engine.network.server.Server server = client.getServerClient().getServer();

                if (inventory.isFullDirty()) {
                    // Full inventory is dirty (e.g., after sorting)
                    // Send all slots individually to ensure client is fully synced
                    for (int i = 0; i < inventory.getSize(); i++) {
                        PacketBankInventoryUpdate updatePacket = new PacketBankInventoryUpdate(
                            ownerAuth,
                            i,
                            inventory.getItem(i)
                        );
                        server.network.sendPacket(updatePacket, client.getServerClient());
                    }
                    inventory.clean();
                    ModLogger.debug("Synced all bank slots for auth=%d (full dirty)", ownerAuth);
                } else {
                    // Sync individual dirty slots
                    for (int i = 0; i < inventory.getSize(); i++) {
                        if (inventory.isDirty(i)) {
                            PacketBankInventoryUpdate updatePacket = new PacketBankInventoryUpdate(
                                ownerAuth,
                                i,
                                inventory.getItem(i)
                            );
                            server.network.sendPacket(updatePacket, client.getServerClient());
                            inventory.clean(i);
                        }
                    }
                }
            }
            // Send coin count update to client if it has changed server-side
            long coinCount = bank.getCoins();
            if (coinCount != lastSyncedCoinCount) {
                lastSyncedCoinCount = coinCount;
                syncCoinCount.runAndSend(coinCount);
                ModLogger.debug("Server: Synced coin count %d for auth=%d", coinCount, ownerAuth);
            }
        }
    }

    /**
     * Create packet content for opening this container.
     * Includes full bank inventory contents for client-side sync.
     */
    public static Packet getOpenPacketContent(long ownerAuth, int upgradeLevel, boolean pinValidated, PlayerBank bank) {
        Packet packet = new Packet();
        PacketWriter writer = new PacketWriter(packet);
        writer.putNextLong(ownerAuth);
        writer.putNextInt(upgradeLevel);
        writer.putNextBoolean(pinValidated);
        // Write whether a PIN is configured on the bank
        writer.putNextBoolean(bank != null && bank.isPinSet());

        // Write coin count
        if (bank != null) {
            writer.putNextLong(bank.getCoins());
        } else {
            writer.putNextLong(0);
        }

        // Write full inventory contents (similar to ObjectEntity.setupContentPacket)
        if (bank != null) {
            bank.getInventory().writeContent(writer);
        } else {
            // Write empty inventory if bank is null
            new necesse.inventory.Inventory(0).writeContent(writer);
        }

        return packet;
    }
}
