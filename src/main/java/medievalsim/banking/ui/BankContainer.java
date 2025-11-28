package medievalsim.banking.ui;

import medievalsim.banking.domain.PlayerBank;
import medievalsim.banking.service.BankClientSnapshot;
import medievalsim.banking.service.BankingResult;
import medievalsim.banking.service.BankingService;
import medievalsim.config.ModConfig;
import medievalsim.packets.PacketBankInventoryUpdate;
import medievalsim.registries.MedievalSimContainers;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;
import necesse.inventory.container.Container;
import necesse.inventory.container.customAction.BooleanCustomAction;
import necesse.inventory.container.customAction.EmptyCustomAction;
import necesse.inventory.container.customAction.IntCustomAction;
import necesse.inventory.container.customAction.LongCustomAction;
import necesse.inventory.container.customAction.StringCustomAction;
import necesse.inventory.container.slots.ContainerSlot;

public class BankContainer extends Container {

    public final PlayerBank bank;
    private final BankingService bankingService;
    public final long ownerAuth;

    public int BANK_INVENTORY_START = -1;
    public int BANK_INVENTORY_END = -1;

    public boolean isPinValidated = false;
    public int currentUpgradeLevel = 0;
    public int maxUpgradeLevel = ModConfig.Banking.maxUpgrades;
    public long clientCoinCount = 0;
    public long lastSyncedCoinCount = 0;

    public EmptyCustomAction purchaseUpgrade;
    public StringCustomAction validatePin;
    public StringCustomAction setNewPin;
    public BooleanCustomAction setPinValidated;
    public BooleanCustomAction setBankPinSet;
    public IntCustomAction depositCoins;
    public IntCustomAction withdrawCoins;
    public LongCustomAction syncCoinCount;

    public EmptyCustomAction sortButton;
    public EmptyCustomAction lootButton;
    public EmptyCustomAction quickStackButton;

    public BankContainer(NetworkClient client, int uniqueSeed, Packet content) {
        super(client, uniqueSeed);

        PacketReader reader = new PacketReader(content);
        this.ownerAuth = reader.getNextLong();
        this.currentUpgradeLevel = reader.getNextInt();
        this.isPinValidated = reader.getNextBoolean();
        boolean pinSet = reader.getNextBoolean();
        this.clientCoinCount = reader.getNextLong();

        if (client.isServer()) {
            BankingService service = new BankingService(client.getServerClient());
            this.bankingService = service;
            this.bank = service.getBank();
            if (this.bank == null) {
                ModLogger.error("Failed to get bank for player auth=%d", ownerAuth);
                throw new IllegalStateException("Bank not found for player");
            }
            this.clientCoinCount = bank.getCoins();
            this.lastSyncedCoinCount = bank.getCoins();
            ModLogger.debug("Server: Loaded bank for auth=%d, level=%d, slots=%d, coins=%d",
                ownerAuth, bank.getUpgradeLevel(), bank.getInventory().getSize(), bank.getCoins());
        } else {
            this.bankingService = null;
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

    private void registerActions() {
        this.purchaseUpgrade = this.registerAction(new EmptyCustomAction() {
            @Override
            protected void run() {
                if (client.isServer()) {
                    handlePurchaseUpgrade();
                }
            }
        });

        this.validatePin = this.registerAction(new StringCustomAction() {
            @Override
            protected void run(String pin) {
                if (client.isServer()) {
                    handleValidatePin(pin);
                }
            }
        });

        this.setNewPin = this.registerAction(new StringCustomAction() {
            @Override
            protected void run(String pin) {
                if (client.isServer()) {
                    handleSetNewPin(pin);
                }
            }
        });

        this.setPinValidated = this.registerAction(new BooleanCustomAction() {
            @Override
            protected void run(boolean validated) {
                isPinValidated = validated;
            }
        });

        this.setBankPinSet = this.registerAction(new BooleanCustomAction() {
            @Override
            protected void run(boolean pinSet) {
                if (!client.isServer()) {
                    bank.setPinSet(pinSet);
                }
            }
        });

        this.depositCoins = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int amount) {
                if (client.isServer()) {
                    handleDepositCoins(amount);
                }
            }
        });

        this.withdrawCoins = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int amount) {
                if (client.isServer()) {
                    handleWithdrawCoins(amount);
                }
            }
        });

        this.syncCoinCount = this.registerAction(new LongCustomAction() {
            @Override
            protected void run(long coinCount) {
                if (!client.isServer()) {
                    clientCoinCount = coinCount;
                    ModLogger.debug("Client: Updated coin count to %d", coinCount);
                }
            }
        });

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
                    bank.getInventory().markFullDirty();
                    ModLogger.debug("Sorted bank inventory for player auth=%d", ownerAuth);
                }
            }
        });

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

        this.quickStackButton = this.registerAction(new EmptyCustomAction() {
            @Override
            protected void run() {
                if (client.isServer()) {
                    for (int i = CLIENT_INVENTORY_START; i <= CLIENT_INVENTORY_END; i++) {
                        if (getSlot(i).isItemLocked() || getSlot(i).isClear()) continue;

                        if (bank.getInventory().getAmount(
                            client.playerMob.getLevel(),
                            client.playerMob,
                            getSlot(i).getItem().item,
                            0,
                            bank.getInventory().getSize() - 1,
                            "quickstack") > 0) {

                            transferToSlots(getSlot(i), BANK_INVENTORY_START, BANK_INVENTORY_END, "quickstack");
                        }
                    }
                    ModLogger.debug("Quick stacked items to bank for player auth=%d", ownerAuth);
                }
            }
        });
    }

    private void setupQuickTransfer() {
        this.addInventoryQuickTransfer(
            BANK_INVENTORY_START,
            BANK_INVENTORY_END
        );
    }

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

    private void handlePurchaseUpgrade() {
        if (bankingService == null) {
            return;
        }
        if (!bankingService.upgradeBank()) {
            ModLogger.warn("Player auth=%d attempted invalid upgrade", ownerAuth);
            return;
        }
        currentUpgradeLevel = bank.getUpgradeLevel();
        ServerClient serverClient = client.getServerClient();
        serverClient.closeContainer(false);
        Packet containerPacket = BankContainer.getOpenPacketContent(
            serverClient.authentication,
            isPinValidated,
            bank
        );
        PacketOpenContainer openPacket = new PacketOpenContainer(
            MedievalSimContainers.BANK_CONTAINER,
            containerPacket
        );
        ContainerRegistry.openAndSendContainer(serverClient, openPacket);
        ModLogger.debug("Reopened bank after upgrade for player auth=%d", ownerAuth);
    }

    private void handleValidatePin(String pin) {
        if (bankingService != null && bankingService.validatePin(pin)) {
            isPinValidated = true;
            setPinValidated.runAndSend(true);
            ModLogger.debug("PIN validated for player auth=%d", ownerAuth);
        } else {
            isPinValidated = false;
            setPinValidated.runAndSend(false);
            ModLogger.debug("PIN validation failed for player auth=%d", ownerAuth);
        }
    }

    private void handleSetNewPin(String pin) {
        if (bankingService != null && bankingService.setPin(pin)) {
            isPinValidated = true;
            setPinValidated.runAndSend(true);
            setBankPinSet.runAndSend(true);
            ModLogger.info("New PIN set for player auth=%d", ownerAuth);
        }
    }

    private void handleDepositCoins(int amount) {
        if (amount <= 0) {
            ModLogger.warn("Player auth=%d attempted to deposit invalid amount: %d", ownerAuth, amount);
            return;
        }

        if (bankingService == null) {
            return;
        }
        BankingResult result = bankingService.depositCoins(amount);
        if (result.isSuccess()) {
            syncCoinCount.runAndSend(bank.getCoins());
        } else if (result.getMessageKey() != null) {
            sendBankMessage(result.getMessageKey());
        }
    }

    private void handleWithdrawCoins(int amount) {
        if (amount <= 0) {
            ModLogger.warn("Player auth=%d attempted to withdraw invalid amount: %d", ownerAuth, amount);
            return;
        }
        if (bankingService == null) {
            return;
        }
        BankingResult result = bankingService.withdrawCoins(amount);
        if (result.isSuccess()) {
            syncCoinCount.runAndSend(bank.getCoins());
            if (result.getRemaining() > 0 && result.getMessageKey() != null) {
                sendBankMessage(result.getMessageKey(), "delivered", String.valueOf(result.getAmountProcessed()), "remaining", String.valueOf(result.getRemaining()));
            }
        } else if (result.getMessageKey() != null) {
            sendBankMessage(result.getMessageKey());
        }
    }

    public boolean canUpgrade() {
        return bank != null && bank.canUpgrade();
    }

    public int getNextUpgradeCost() {
        return bank != null ? bank.getNextUpgradeCost() : 0;
    }

    public int getCurrentSlots() {
        return ModConfig.Banking.getTotalSlots(currentUpgradeLevel);
    }

    public int getMaxSlots() {
        return ModConfig.Banking.getTotalSlots(maxUpgradeLevel);
    }

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

        if (client.isServer()) {
            necesse.inventory.Inventory inventory = bank.getInventory();

            if (inventory.isDirty()) {
                necesse.engine.network.server.Server server = client.getServerClient().getServer();

                if (inventory.isFullDirty()) {
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
            long coinCount = bank.getCoins();
            if (coinCount != lastSyncedCoinCount) {
                lastSyncedCoinCount = coinCount;
                syncCoinCount.runAndSend(coinCount);
                ModLogger.debug("Server: Synced coin count %d for auth=%d", coinCount, ownerAuth);
            }
        }
    }

    public static Packet getOpenPacketContent(long ownerAuth, boolean pinValidated, PlayerBank bank) {
        Packet packet = new Packet();
        PacketWriter writer = new PacketWriter(packet);
        BankClientSnapshot snapshot = BankClientSnapshot.fromBank(ownerAuth, bank);
        snapshot.writeOpenPacket(writer, pinValidated);
        return packet;
    }
}
