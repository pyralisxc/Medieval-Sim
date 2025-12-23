package medievalsim.grandexchange.ui.handlers;

import medievalsim.banking.domain.PlayerBank;
import medievalsim.config.ModConfig;
import medievalsim.grandexchange.application.GrandExchangeLedger;
import medievalsim.grandexchange.domain.CollectionItem;
import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.grandexchange.domain.MarketSnapshot;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.model.event.SellOfferSaleEvent;
import medievalsim.grandexchange.model.snapshot.DefaultsConfigSnapshot;
import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.GEFeedbackLevel;
import medievalsim.grandexchange.net.SellActionResultCode;
import medievalsim.grandexchange.net.SellActionResultMessage;
import medievalsim.grandexchange.net.SellActionType;
import medievalsim.grandexchange.services.RateLimitedAction;
import medievalsim.grandexchange.services.RateLimitService;
import medievalsim.grandexchange.services.RateLimitStatus;
import medievalsim.grandexchange.util.CollectionPaginator;
import medievalsim.packets.PacketGEAutoClearSync;
import medievalsim.packets.PacketGEBuyOrderSync;
import medievalsim.packets.PacketGEDefaultsConfig;
import medievalsim.packets.PacketGEFeedback;
import medievalsim.packets.PacketGEHistorySync;
import medievalsim.packets.PacketGESaleEvent;
import medievalsim.packets.PacketGESellActionResult;
import medievalsim.packets.PacketGESync;
import medievalsim.util.ModLogger;
import necesse.engine.Settings;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.Container;
import necesse.inventory.container.customAction.LongCustomAction;

import java.util.List;

/**
 * Centralized manager for all Grand Exchange sync packet operations.
 * Extracted from GrandExchangeContainer to reduce its size and improve testability.
 * 
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Sending sync packets to clients (buy orders, sell inventory, collection, history)</li>
 *   <li>Broadcasting configuration updates to all connected GE containers</li>
 *   <li>Providing feedback messages to clients</li>
 *   <li>Managing coin count synchronization</li>
 * </ul>
 */
public class ContainerSyncManager {

    private final ServerClient serverClient;
    private final long playerAuth;
    private final PlayerGEInventory playerInventory;
    private final GrandExchangeLevelData geData;
    private final GrandExchangeLedger ledger;
    private final PlayerBank bank;
    private final LongCustomAction syncCoinCountAction;

    // Cached pagination state (updated after each collection sync)
    private int collectionPageIndex = 0;
    private int collectionTotalPages = 1;
    private int collectionTotalItems = 0;
    private int collectionPageSize = ModConfig.GrandExchange.getCollectionPageSize();
    private boolean collectionDepositToBankPreferred = ModConfig.GrandExchange.getDefaultCollectionDepositPreference();
    private int[] collectionPageGlobalIndices = new int[0];

    public ContainerSyncManager(
            ServerClient serverClient,
            long playerAuth,
            PlayerGEInventory playerInventory,
            GrandExchangeLevelData geData,
            GrandExchangeLedger ledger,
            PlayerBank bank,
            LongCustomAction syncCoinCountAction) {
        this.serverClient = serverClient;
        this.playerAuth = playerAuth;
        this.playerInventory = playerInventory;
        this.geData = geData;
        this.ledger = ledger;
        this.bank = bank;
        this.syncCoinCountAction = syncCoinCountAction;
    }

    // ===== INITIAL SYNC =====

    /**
     * Send all initial sync packets when the container opens.
     */
    public void sendInitialSyncToClient() {
        if (serverClient == null) {
            return;
        }
        sendBuyOrderSync();
        sendSellInventorySync();
        sendPendingSaleEventsSnapshot();
        sendCollectionSync();
        sendDefaultsConfigSnapshot();
        sendHistorySnapshot();
        ModLogger.debug("Sent initial GE sync packets to client auth=%d", playerAuth);
    }

    // ===== BUY ORDER SYNC =====

    /**
     * Send buy order sync packet to client.
     */
    public void sendBuyOrderSync() {
        if (serverClient == null) {
            return;
        }
        RateLimitStatus creationStatus = ledger != null
            ? ledger.getRateLimitStatus(RateLimitedAction.BUY_CREATE, playerAuth)
            : RateLimitStatus.inactive(RateLimitedAction.BUY_CREATE);
        RateLimitStatus toggleStatus = ledger != null
            ? ledger.getRateLimitStatus(RateLimitedAction.BUY_TOGGLE, playerAuth)
            : RateLimitStatus.inactive(RateLimitedAction.BUY_TOGGLE);

        PacketGEBuyOrderSync packet = new PacketGEBuyOrderSync(
            playerAuth,
            playerInventory.getBuyOrders(),
            ledger != null ? ledger.getAnalyticsService() : null,
            playerInventory,
            creationStatus,
            toggleStatus
        );

        serverClient.sendPacket(packet);
        ModLogger.debug("Sent buy order sync to player auth=%d", playerAuth);
    }

    // ===== SELL INVENTORY SYNC =====

    /**
     * Send sell inventory sync packet to client.
     */
    public void sendSellInventorySync() {
        if (serverClient == null) {
            return;
        }
        medievalsim.packets.PacketGESellInventorySync packet =
            new medievalsim.packets.PacketGESellInventorySync(
                playerAuth,
                playerInventory.getSellOffers()
            );
        serverClient.sendPacket(packet);
        ModLogger.debug("Sent sell inventory sync to player auth=%d", playerAuth);
    }

    /**
     * Send pending sale events that accumulated while the player was offline.
     */
    public void sendPendingSaleEventsSnapshot() {
        if (serverClient == null) {
            return;
        }
        List<SellOfferSaleEvent> pending = playerInventory.drainPendingSaleEvents();
        if (pending.isEmpty()) {
            return;
        }
        for (SellOfferSaleEvent event : pending) {
            serverClient.sendPacket(new PacketGESaleEvent(playerAuth, event));
        }
        if (geData != null) {
            geData.requestPersistenceSave();
        }
        ModLogger.debug("Flushed %d pending sale events to player auth=%d", pending.size(), playerAuth);
    }

    // ===== COLLECTION SYNC =====

    /**
     * Send collection box sync packet to client.
     */
    public void sendCollectionSync() {
        if (serverClient == null) {
            return;
        }

        CollectionPaginator.Page page = CollectionPaginator.paginate(
            playerInventory.getCollectionBox(),
            playerInventory.getCollectionPageIndex()
        );
        playerInventory.setCollectionPageIndex(page.getPageIndex());
        updateCollectionPageMetadata(page);

        medievalsim.packets.PacketGECollectionSync packet =
            new medievalsim.packets.PacketGECollectionSync(
                playerAuth,
                page,
                playerInventory.isCollectionDepositToBankPreferred(),
                playerInventory.isAutoSendToBank(),
                playerInventory.isNotifyPartialSales(),
                playerInventory.isPlaySoundOnSale()
            );

        serverClient.sendPacket(packet);
        ModLogger.debug("Sent collection sync to player auth=%d (page %d/%d)",
            playerAuth, page.getPageIndex() + 1, page.getTotalPages());
    }

    private void updateCollectionPageMetadata(CollectionPaginator.Page page) {
        this.collectionPageIndex = page.getPageIndex();
        this.collectionTotalPages = page.getTotalPages();
        this.collectionTotalItems = page.getTotalItems();
        this.collectionPageSize = page.getPageSize();
        this.collectionDepositToBankPreferred = playerInventory.isCollectionDepositToBankPreferred();
        List<CollectionPaginator.Entry> entries = page.getEntries();
        this.collectionPageGlobalIndices = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            this.collectionPageGlobalIndices[i] = entries.get(i).getGlobalIndex();
        }
    }

    // ===== HISTORY SYNC =====

    /**
     * Send history tab snapshot to client.
     */
    public void sendHistorySnapshot() {
        if (serverClient == null) {
            return;
        }
        serverClient.sendPacket(new PacketGEHistorySync(playerAuth, playerInventory));
    }

    // ===== DEFAULTS CONFIG SYNC =====

    /**
     * Send defaults config snapshot to the current client (if they're the server owner).
     */
    public void sendDefaultsConfigSnapshot() {
        if (serverClient == null || !isServerOwner(serverClient)) {
            return;
        }
        serverClient.sendPacket(new PacketGEDefaultsConfig(buildDefaultsConfigSnapshot(serverClient.authentication)));
    }

    /**
     * Broadcast defaults config to all connected server owners who have the GE open.
     */
    public void broadcastDefaultsConfigSnapshot() {
        if (serverClient == null || serverClient.getServer() == null) {
            return;
        }
        for (ServerClient other : serverClient.getServer().getClients()) {
            if (other == null || !isServerOwner(other)) {
                continue;
            }
            other.sendPacket(new PacketGEDefaultsConfig(buildDefaultsConfigSnapshot(other.authentication)));
        }
    }

    /**
     * Broadcast auto-clear preference update to all connected GE containers.
     */
    public void broadcastAutoClearUpdate(boolean enabled) {
        if (serverClient == null || serverClient.getServer() == null) {
            return;
        }
        for (ServerClient other : serverClient.getServer().getClients()) {
            if (other == null) {
                continue;
            }
            Container openContainer = other.getContainer();
            if (openContainer instanceof medievalsim.grandexchange.ui.GrandExchangeContainer geContainer) {
                geContainer.applyAutoClearSync(enabled);
                other.sendPacket(new PacketGEAutoClearSync(enabled));
            }
        }
    }

    public DefaultsConfigSnapshot buildDefaultsConfigSnapshot(long ownerAuthValue) {
        int sellSlotMin = 5;
        int sellSlotMax = 20;
        int buySlotMin = 1;
        int buySlotMax = 10;
        boolean autoClearEnabled = ModConfig.GrandExchange.autoClearSellStagingSlot;
        int stagingSlotIndex = 0; // Will be overridden by container if different

        return new DefaultsConfigSnapshot(
            ownerAuthValue,
            sellSlotMin,
            sellSlotMax,
            ModConfig.GrandExchange.geInventorySlots,
            buySlotMin,
            buySlotMax,
            ModConfig.GrandExchange.buyOrderSlots,
            autoClearEnabled,
            stagingSlotIndex
        );
    }

    // ===== MARKET SYNC =====

    /**
     * Send market snapshot packet to client.
     */
    public void sendMarketSnapshot(MarketSnapshot snapshot) {
        if (serverClient == null || snapshot == null) {
            return;
        }
        serverClient.sendPacket(new PacketGESync(playerAuth, snapshot));
    }

    // ===== COIN COUNT SYNC =====

    /**
     * Sync current bank coin count to client.
     */
    public void syncCoinCount() {
        if (bank != null && syncCoinCountAction != null) {
            syncCoinCountAction.runAndSend(bank.getCoins());
        }
    }

    /**
     * Sync specific coin count to client.
     */
    public void syncCoinCount(long coinCount) {
        if (syncCoinCountAction != null) {
            syncCoinCountAction.runAndSend(coinCount);
        }
    }

    // ===== FEEDBACK MESSAGES =====

    /**
     * Send feedback message to client via packet.
     */
    public void sendFeedback(GEFeedbackChannel channel, GEFeedbackLevel level, String message) {
        if (serverClient == null || message == null || message.isBlank()) {
            return;
        }
        serverClient.sendPacket(new PacketGEFeedback(
            playerAuth, channel, level, message, System.currentTimeMillis()));
    }

    /**
     * Send sell action result to client with cooldown info.
     */
    public void sendSellActionResult(SellActionType action,
                                     SellActionResultCode code,
                                     int slotIndex,
                                     String message,
                                     float cooldownSeconds) {
        if (serverClient == null) {
            return;
        }
        SellActionResultMessage payload = new SellActionResultMessage(
            action, code, slotIndex, message, cooldownSeconds);
        serverClient.sendPacket(new PacketGESellActionResult(payload));
        sendSellChatFeedback(payload);
        sendFeedback(
            GEFeedbackChannel.SELL,
            payload.isSuccess() ? GEFeedbackLevel.INFO : GEFeedbackLevel.ERROR,
            payload.getMessage());
    }

    private void sendSellChatFeedback(SellActionResultMessage payload) {
        if (serverClient == null || payload == null) {
            return;
        }
        serverClient.sendChatMessage(new StaticMessage(String.format("[GE] %s", payload.getMessage())));
    }

    /**
     * Send admin message via chat.
     */
    public void sendAdminMessage(String message) {
        if (serverClient != null && message != null) {
            serverClient.sendChatMessage(message);
        }
    }

    // ===== COLLECTION PAGINATION STATE =====

    public int getCollectionPageIndex() {
        return collectionPageIndex;
    }

    public int getCollectionTotalPages() {
        return collectionTotalPages;
    }

    public int getCollectionTotalItems() {
        return collectionTotalItems;
    }

    public int getCollectionPageSize() {
        return collectionPageSize;
    }

    public boolean isCollectionDepositToBankPreferred() {
        return collectionDepositToBankPreferred;
    }

    public int[] getCollectionPageGlobalIndices() {
        return collectionPageGlobalIndices;
    }

    /**
     * Resolve a local page index to a global collection index.
     */
    public int resolveGlobalIndex(int localIndex) {
        if (collectionPageGlobalIndices == null || localIndex < 0 || localIndex >= collectionPageGlobalIndices.length) {
            return localIndex;
        }
        return collectionPageGlobalIndices[localIndex];
    }

    /**
     * Check if a local collection index is valid for the current page.
     */
    public boolean isValidLocalCollectionIndex(int localIndex) {
        return collectionPageGlobalIndices != null
            && localIndex >= 0
            && localIndex < collectionPageGlobalIndices.length;
    }

    // ===== UTILITY =====

    public boolean isServerOwner(ServerClient client) {
        if (client == null) {
            return false;
        }
        if (client.getPermissionLevel() == PermissionLevel.OWNER) {
            return true;
        }
        return Settings.serverOwnerAuth != -1L && client.authentication == Settings.serverOwnerAuth;
    }

    public ServerClient getServerClient() {
        return serverClient;
    }

    public long getPlayerAuth() {
        return playerAuth;
    }

    public PlayerGEInventory getPlayerInventory() {
        return playerInventory;
    }

    public GrandExchangeLevelData getGeData() {
        return geData;
    }

    public GrandExchangeLedger getLedger() {
        return ledger;
    }

    public PlayerBank getBank() {
        return bank;
    }
}
