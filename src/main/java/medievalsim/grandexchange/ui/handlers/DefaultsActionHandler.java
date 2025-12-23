package medievalsim.grandexchange.ui.handlers;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.util.ModLogger;
import necesse.engine.Settings;
import necesse.engine.network.server.ServerClient;

/**
 * Handles admin/defaults configuration operations for the Grand Exchange.
 * Extracted from GrandExchangeContainer to reduce its size and improve testability.
 * 
 * <p>Operations handled (world owner only):</p>
 * <ul>
 *   <li>Update sell slot count - Configure number of sell slots per player</li>
 *   <li>Update buy slot count - Configure number of buy order slots per player</li>
 *   <li>Update auto-clear preference - Configure automatic staging slot clearing</li>
 * </ul>
 * 
 * <p>All operations require world owner permission.</p>
 */
public class DefaultsActionHandler {

    private static final int SELL_SLOT_MIN = 5;
    private static final int SELL_SLOT_MAX = 20;
    private static final int BUY_SLOT_MIN = 1;
    private static final int BUY_SLOT_MAX = 10;

    private final ServerClient serverClient;
    private final long playerAuth;
    private final GrandExchangeLevelData geData;
    private final ContainerSyncManager syncManager;
    private final boolean isWorldOwner;

    // Local auto-clear state (synced with ModConfig)
    private boolean autoClearEnabled;

    public DefaultsActionHandler(
            ServerClient serverClient,
            long playerAuth,
            GrandExchangeLevelData geData,
            ContainerSyncManager syncManager,
            boolean isWorldOwner) {
        this.serverClient = serverClient;
        this.playerAuth = playerAuth;
        this.geData = geData;
        this.syncManager = syncManager;
        this.isWorldOwner = isWorldOwner;
        this.autoClearEnabled = ModConfig.GrandExchange.autoClearSellStagingSlot;
    }

    // ===== UPDATE SELL SLOT CONFIG =====

    /**
     * Update the number of sell slots available per player.
     * Requires world owner permission.
     * 
     * @param requestedSlots The new slot count (must be between 5-20)
     */
    public void handleUpdateSellSlotConfig(int requestedSlots) {
        if (serverClient == null) {
            return;
        }
        if (!isWorldOwner) {
            syncManager.sendAdminMessage("Only the world owner can change Grand Exchange slots.");
            return;
        }
        if (requestedSlots < SELL_SLOT_MIN || requestedSlots > SELL_SLOT_MAX) {
            syncManager.sendAdminMessage(String.format("Sell slots must be between %d and %d.", SELL_SLOT_MIN, SELL_SLOT_MAX));
            return;
        }
        int current = ModConfig.GrandExchange.geInventorySlots;
        if (requestedSlots == current) {
            syncManager.sendAdminMessage("Sell slot count is already set to that value.");
            return;
        }
        if (requestedSlots < current && geData != null && !geData.canApplySellSlotCount(requestedSlots)) {
            syncManager.sendAdminMessage("Cannot reduce sell slots while higher slots still contain items or offers.");
            return;
        }
        
        ModConfig.GrandExchange.setGeInventorySlots(requestedSlots);
        if (geData != null) {
            geData.resizeAllSellInventories(requestedSlots);
        }
        Settings.saveServerSettings();
        syncManager.broadcastDefaultsConfigSnapshot();
        
        ModLogger.info("World owner %d updated GE sell slots to %d", playerAuth, requestedSlots);
        syncManager.sendAdminMessage(String.format("Sell slots updated to %d. Reopen the GE to apply changes.", requestedSlots));
    }

    // ===== UPDATE BUY SLOT CONFIG =====

    /**
     * Update the number of buy order slots available per player.
     * Requires world owner permission.
     * 
     * @param requestedSlots The new slot count (must be between 1-10)
     */
    public void handleUpdateBuySlotConfig(int requestedSlots) {
        if (serverClient == null) {
            return;
        }
        if (!isWorldOwner) {
            syncManager.sendAdminMessage("Only the world owner can change Grand Exchange slots.");
            return;
        }
        if (requestedSlots < BUY_SLOT_MIN || requestedSlots > BUY_SLOT_MAX) {
            syncManager.sendAdminMessage(String.format("Buy order slots must be between %d and %d.", BUY_SLOT_MIN, BUY_SLOT_MAX));
            return;
        }
        int current = ModConfig.GrandExchange.buyOrderSlots;
        if (requestedSlots == current) {
            syncManager.sendAdminMessage("Buy order slot count is already set to that value.");
            return;
        }
        if (requestedSlots < current && geData != null && !geData.canApplyBuySlotCount(requestedSlots)) {
            syncManager.sendAdminMessage("Cannot reduce buy order slots while higher slots still contain orders.");
            return;
        }
        
        ModConfig.GrandExchange.setBuyOrderSlots(requestedSlots);
        if (geData != null) {
            geData.resizeAllBuyInventories(requestedSlots);
        }
        Settings.saveServerSettings();
        syncManager.broadcastDefaultsConfigSnapshot();
        
        ModLogger.info("World owner %d updated GE buy order slots to %d", playerAuth, requestedSlots);
        syncManager.sendAdminMessage(String.format("Buy order slots updated to %d. Reopen the GE to apply changes.", requestedSlots));
    }

    // ===== UPDATE AUTO-CLEAR PREFERENCE =====

    /**
     * Update the server-wide auto-clear sell staging slot preference.
     * Requires world owner permission.
     * 
     * @param enabled Whether auto-clear should be enabled
     */
    public void handleUpdateAutoClearPreference(boolean enabled) {
        if (serverClient == null) {
            return;
        }
        if (!isWorldOwner) {
            syncManager.sendAdminMessage("Only the world owner can change Grand Exchange auto-clear.");
            return;
        }
        if (ModConfig.GrandExchange.autoClearSellStagingSlot == enabled) {
            syncManager.sendAdminMessage("Sell staging auto-clear is already set to that value.");
            return;
        }
        
        ModConfig.GrandExchange.setAutoClearSellStagingSlot(enabled);
        Settings.saveServerSettings();
        this.autoClearEnabled = enabled;
        syncManager.broadcastAutoClearUpdate(enabled);
        syncManager.broadcastDefaultsConfigSnapshot();
        
        syncManager.sendAdminMessage(enabled
            ? "Sell staging auto-clear enabled. Players may need to reopen the GE to see the change."
            : "Sell staging auto-clear disabled. Players may need to reopen the GE to see the change.");
    }

    // ===== GETTERS =====

    public boolean isAutoClearEnabled() {
        return autoClearEnabled;
    }

    public void setAutoClearEnabled(boolean enabled) {
        this.autoClearEnabled = enabled;
    }

    public boolean isWorldOwner() {
        return isWorldOwner;
    }

    public static int getSellSlotMin() {
        return SELL_SLOT_MIN;
    }

    public static int getSellSlotMax() {
        return SELL_SLOT_MAX;
    }

    public static int getBuySlotMin() {
        return BUY_SLOT_MIN;
    }

    public static int getBuySlotMax() {
        return BUY_SLOT_MAX;
    }
}
