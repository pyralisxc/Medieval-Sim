package medievalsim.grandexchange.ui.handlers;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.application.workflow.SellOfferWorkflow;
import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.GEFeedbackLevel;
import medievalsim.grandexchange.net.SellActionResultCode;
import medievalsim.grandexchange.net.SellActionType;
import medievalsim.grandexchange.services.RateLimitService;
import medievalsim.util.ModLogger;
import necesse.engine.network.server.ServerClient;

/**
 * Handles all sell offer actions for the Grand Exchange.
 * Extracted from GrandExchangeContainer to reduce its size and improve testability.
 * 
 * <p>Operations handled:</p>
 * <ul>
 *   <li>Create - Stage a new draft sell offer</li>
 *   <li>Enable - Activate offer to appear on market</li>
 *   <li>Disable - Hide offer from market (still owned)</li>
 *   <li>Cancel - Remove offer entirely, return items</li>
 *   <li>Reclaim - Recover legacy hidden inventory items</li>
 * </ul>
 */
public class SellOfferActionHandler {

    private final ServerClient serverClient;
    private final long playerAuth;
    private final PlayerGEInventory playerInventory;
    private final GrandExchangeLevelData geData;
    private final SellOfferWorkflow sellWorkflow;
    private final ContainerSyncManager syncManager;

    // Pending action tracking for result callbacks
    private int pendingSellSlot = -1;
    private SellActionType pendingSellAction = SellActionType.CREATE;

    public SellOfferActionHandler(
            ServerClient serverClient,
            long playerAuth,
            PlayerGEInventory playerInventory,
            GrandExchangeLevelData geData,
            SellOfferWorkflow sellWorkflow,
            ContainerSyncManager syncManager) {
        this.serverClient = serverClient;
        this.playerAuth = playerAuth;
        this.playerInventory = playerInventory;
        this.geData = geData;
        this.sellWorkflow = sellWorkflow;
        this.syncManager = syncManager;
    }

    // ===== CREATE SELL OFFER =====

    /**
     * Create a new sell offer in DRAFT state.
     */
    public void handleCreate(int slotIndex, int pricePerItem) {
        if (serverClient == null || sellWorkflow == null) {
            return;
        }
        markPendingAction(slotIndex, SellActionType.CREATE);
        SellOfferWorkflow.ActionResult result = sellWorkflow.stageDraft(
            serverClient, playerInventory, playerAuth, slotIndex, pricePerItem);
        
        if (result.isSuccess()) {
            syncManager.sendSellInventorySync();
            syncManager.sendHistorySnapshot();
        }
        
        int responseSlot = result.getSlotIndex() >= 0 ? result.getSlotIndex() : slotIndex;
        float cooldownSeconds = resolveCooldown(result.getCode());
        String message = buildMessage(result.getAction(), result.getCode(), responseSlot, cooldownSeconds);
        syncManager.sendSellActionResult(result.getAction(), result.getCode(), responseSlot, message, cooldownSeconds);
    }

    // ===== ENABLE SELL OFFER =====

    /**
     * Enable a sell offer to appear on the market.
     */
    public void handleEnable(int slotIndex) {
        if (serverClient == null || sellWorkflow == null) {
            return;
        }
        markPendingAction(slotIndex, SellActionType.ENABLE);
        SellOfferWorkflow.ActionResult result = sellWorkflow.enable(serverClient, playerAuth, slotIndex);
        
        if (result.isSuccess()) {
            syncManager.sendSellInventorySync();
            // Note: Caller should also call refreshMarketListings() since the offer now appears on market
        }
        
        float cooldownSeconds = resolveCooldown(result.getCode());
        String message = buildMessage(result.getAction(), result.getCode(), slotIndex, cooldownSeconds);
        syncManager.sendSellActionResult(result.getAction(), result.getCode(), slotIndex, message, cooldownSeconds);
    }

    // ===== DISABLE SELL OFFER =====

    /**
     * Disable a sell offer to hide it from the market.
     */
    public void handleDisable(int slotIndex) {
        if (serverClient == null || sellWorkflow == null) {
            return;
        }
        markPendingAction(slotIndex, SellActionType.DISABLE);
        SellOfferWorkflow.ActionResult result = sellWorkflow.disable(serverClient, playerAuth, slotIndex);
        
        if (result.isSuccess()) {
            syncManager.sendSellInventorySync();
            // Note: Caller should also call refreshMarketListings() since the offer was removed from market
        }
        
        float cooldownSeconds = resolveCooldown(result.getCode());
        String message = buildMessage(result.getAction(), result.getCode(), slotIndex, cooldownSeconds);
        syncManager.sendSellActionResult(result.getAction(), result.getCode(), slotIndex, message, cooldownSeconds);
    }

    // ===== CANCEL SELL OFFER =====

    /**
     * Cancel a sell offer entirely, returning items to the player.
     */
    public void handleCancel(int slotIndex) {
        if (serverClient == null || sellWorkflow == null) {
            syncManager.sendSellInventorySync(); // Always sync to clear pending state
            return;
        }
        
        boolean canceled = sellWorkflow.cancel(serverClient, playerInventory, playerAuth, slotIndex);
        
        if (canceled) {
            syncManager.sendFeedback(GEFeedbackChannel.SELL, GEFeedbackLevel.INFO, "Offer cancelled. Items returned.");
            syncManager.sendSellInventorySync();
            syncManager.sendHistorySnapshot();
            // Note: Caller should also call refreshMarketListings() if the cancelled offer was on market
        } else {
            syncManager.sendFeedback(GEFeedbackChannel.SELL, GEFeedbackLevel.ERROR, "Failed to cancel offer.");
            syncManager.sendSellInventorySync(); // Sync even on failure to clear pending state
        }
    }

    // ===== RECLAIM LEGACY INVENTORY =====

    /**
     * Reclaim any hidden sell inventory items from legacy data.
     */
    public void reclaimHiddenInventory() {
        if (sellWorkflow == null || serverClient == null) {
            return;
        }
        boolean collectionUpdated = sellWorkflow.reclaimLegacyInventory(serverClient, playerInventory);
        if (collectionUpdated) {
            syncManager.sendCollectionSync();
        }
    }

    // ===== HELPER METHODS =====

    private void markPendingAction(int slotIndex, SellActionType action) {
        pendingSellSlot = slotIndex;
        pendingSellAction = action;
    }

    /**
     * Get pending sell slot for result matching.
     */
    public int getPendingSellSlot() {
        return pendingSellSlot;
    }

    /**
     * Get pending sell action type for result matching.
     */
    public SellActionType getPendingSellAction() {
        return pendingSellAction;
    }

    /**
     * Clear pending action tracking after result received.
     */
    public void clearPendingAction() {
        pendingSellSlot = -1;
    }

    private float resolveCooldown(SellActionResultCode code) {
        if (code == null || geData == null) {
            return 0f;
        }
        RateLimitService rateLimitService = geData.getRateLimitService();
        if (rateLimitService == null) {
            return 0f;
        }
        if (code == SellActionResultCode.RATE_LIMITED) {
            return rateLimitService.getRemainingCooldownForSellOffer(playerAuth);
        }
        if (code == SellActionResultCode.TOGGLE_COOLDOWN) {
            return rateLimitService.getRemainingToggleCooldown(playerAuth);
        }
        return 0f;
    }

    private String buildMessage(SellActionType action,
                                SellActionResultCode code,
                                int slotIndex,
                                float cooldownSeconds) {
        String slotLabel = slotIndex >= 0 ? String.format("slot %d", slotIndex + 1) : "that slot";
        
        switch (code) {
            case SUCCESS:
                return switch (action) {
                    case CREATE -> String.format("Offer posted to %s", slotLabel);
                    case ENABLE -> String.format("%s is now visible on the market", capitalize(slotLabel));
                    case DISABLE -> String.format("%s hidden from market", capitalize(slotLabel));
                    case CANCEL -> String.format("%s cancelled", capitalize(slotLabel));
                };
            case INVALID_SLOT:
                return "Pick a valid offer slot";
            case NO_ITEM_IN_SLOT:
                return "Place an item in the slot first";
            case NO_AVAILABLE_SLOT:
                return "All sell slots are occupied";
            case PRICE_OUT_OF_RANGE:
                return String.format("Price must be %d-%d coins",
                    ModConfig.GrandExchange.minPricePerItem,
                    ModConfig.GrandExchange.maxPricePerItem);
            case MAX_ACTIVE_REACHED:
                return "Max active offers reached";
            case INVALID_ITEM_STATE:
                return "Item changed before posting. Reinsert it.";
            case NO_OFFER_IN_SLOT:
                return "No offer found in that slot";
            case OFFER_STATE_LOCKED:
                return "Offer can no longer change state";
            case TOGGLE_COOLDOWN:
                return cooldownSeconds > 0
                    ? String.format("Wait %.1fs before toggling again", cooldownSeconds)
                    : "Toggling too quickly";
            case RATE_LIMITED:
                return cooldownSeconds > 0
                    ? String.format("Slow down - %.1fs cooldown", cooldownSeconds)
                    : "Posting too fast";
            case SERVER_REJECTED:
                return "Server rejected the action";
            case CLIENT_NOT_READY:
                return "Grand Exchange is still loading";
            case UNKNOWN_FAILURE:
            default:
                return "Action failed, try again";
        }
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value != null ? value : "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
