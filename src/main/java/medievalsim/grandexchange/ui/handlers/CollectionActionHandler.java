package medievalsim.grandexchange.ui.handlers;

import medievalsim.banking.domain.PlayerBank;
import medievalsim.banking.service.BankingService;
import medievalsim.grandexchange.domain.CollectionItem;
import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.GEFeedbackLevel;
import medievalsim.util.ModLogger;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles all collection box operations for the Grand Exchange.
 * Extracted from GrandExchangeContainer to reduce its size and improve testability.
 * 
 * <p>Operations handled:</p>
 * <ul>
 *   <li>Collect single item - Move one entry to inventory or bank</li>
 *   <li>Collect all - Mass collect all entries to bank</li>
 *   <li>Collect selected - Batch collect marked entries</li>
 *   <li>Pagination - Navigate collection pages</li>
 *   <li>Preferences - Toggle deposit destination preference</li>
 * </ul>
 */
public class CollectionActionHandler {

    private final ServerClient serverClient;
    private final long playerAuth;
    private final PlayerGEInventory playerInventory;
    private final GrandExchangeLevelData geData;
    private final BankingService bankingService;
    private final PlayerBank bank;
    private final ContainerSyncManager syncManager;

    public CollectionActionHandler(
            ServerClient serverClient,
            long playerAuth,
            PlayerGEInventory playerInventory,
            GrandExchangeLevelData geData,
            BankingService bankingService,
            PlayerBank bank,
            ContainerSyncManager syncManager) {
        this.serverClient = serverClient;
        this.playerAuth = playerAuth;
        this.playerInventory = playerInventory;
        this.geData = geData;
        this.bankingService = bankingService;
        this.bank = bank;
        this.syncManager = syncManager;
    }

    // ===== COLLECT SINGLE ITEM =====

    /**
     * Collect a single item from the collection box.
     * Uses the player's deposit preference to decide destination.
     */
    public void handleCollectItem(int collectionIndex) {
        if (!syncManager.isValidLocalCollectionIndex(collectionIndex)) {
            ModLogger.warn("Invalid collection selection %d (page length=%d)",
                collectionIndex, syncManager.getCollectionPageGlobalIndices().length);
            syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.ERROR,
                "Select a valid collection entry first.");
            return;
        }
        
        int globalIndex = syncManager.resolveGlobalIndex(collectionIndex);
        boolean preferBank = playerInventory.isCollectionDepositToBankPreferred();
        boolean success = collectSingleEntry(globalIndex, preferBank);
        
        syncManager.sendCollectionSync();
        
        if (success) {
            String destination = preferBank ? "bank" : "inventory";
            syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.INFO,
                String.format("Moved entry to %s.", destination));
        } else {
            syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.ERROR,
                "Unable to move that entry right now.");
        }
    }

    // ===== COLLECT ALL TO BANK =====

    /**
     * Collect all items from the collection box directly to the bank.
     * Processes in reverse order to avoid index shifting issues.
     */
    public void handleCollectAllToBank() {
        if (bankingService == null || bank == null) {
            ModLogger.warn("Banking service not available for collect all");
            syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.ERROR,
                "Bank connection is unavailable.");
            return;
        }

        int collectedCount = 0;
        int failedCount = 0;

        for (int i = playerInventory.getCollectionBoxSize() - 1; i >= 0; i--) {
            CollectionItem item = playerInventory.removeFromCollectionBox(i);
            if (item == null) {
                continue;
            }

            InventoryItem invItem = new InventoryItem(item.getItemStringID(), item.getQuantity());
            boolean added = bank.getInventory().addItem(
                serverClient.getLevel(),
                serverClient.playerMob,
                invItem,
                "grandexchange_collect_all",
                null
            );

            if (added) {
                collectedCount++;
            } else {
                failedCount++;
                playerInventory.insertIntoCollectionBox(i, item);
                ModLogger.warn("Failed to add item to bank: %s x%d",
                    item.getItemStringID(), item.getQuantity());
            }
        }

        ModLogger.info("Collected %d items to bank for player auth=%d (%d failed)",
            collectedCount, playerAuth, failedCount);

        syncManager.sendCollectionSync();
        
        if (collectedCount == 0) {
            syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.WARN,
                "No collection entries were moved to the bank.");
        } else if (failedCount > 0) {
            syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.WARN,
                String.format("Moved %d entries to the bank; %d could not be deposited.", collectedCount, failedCount));
        } else {
            syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.INFO,
                String.format("Moved %d entries to the bank.", collectedCount));
        }
    }

    // ===== COLLECT SELECTED ENTRIES =====

    /**
     * Collect multiple selected entries using a bitmask.
     * 
     * @param selectionMask Bitmask where bit i indicates whether page entry i is selected
     * @param sendToBank If true, deposit to bank; if false, deposit to inventory
     */
    public void handleCollectSelected(long selectionMask, boolean sendToBank) {
        if (selectionMask == 0) {
            syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.ERROR,
                "Select at least one entry before collecting.");
            return;
        }
        
        int[] pageIndices = syncManager.getCollectionPageGlobalIndices();
        List<Integer> globalIndices = new ArrayList<>();
        
        for (int i = 0; i < pageIndices.length; i++) {
            if (((selectionMask >> i) & 1L) == 1L) {
                globalIndices.add(pageIndices[i]);
            }
        }
        
        if (globalIndices.isEmpty()) {
            ModLogger.debug("No collection entries matched selection mask %d", selectionMask);
            syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.WARN,
                "Those entries are no longer available.");
            return;
        }
        
        // Sort in reverse order to collect from end first (prevents index shifting)
        globalIndices.sort(Collections.reverseOrder());
        
        int successCount = 0;
        for (int globalIndex : globalIndices) {
            if (collectSingleEntry(globalIndex, sendToBank)) {
                successCount++;
            }
        }
        
        syncManager.sendCollectionSync();
        
        if (successCount == 0) {
            syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.WARN,
                "No entries could be moved.");
            return;
        }
        
        String destination = sendToBank ? "bank" : "inventory";
        syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.INFO,
            String.format("Moved %d selected entries to the %s.", successCount, destination));
    }

    // ===== PAGINATION =====

    /**
     * Set the current collection page and sync to client.
     */
    public void handleSetCollectionPage(int requestedPage) {
        playerInventory.setCollectionPageIndex(requestedPage);
        syncManager.sendCollectionSync();
        syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.INFO,
            String.format("Loading page %d.", requestedPage + 1));
    }

    // ===== PREFERENCES =====

    /**
     * Toggle the default deposit destination preference.
     */
    public void handleToggleCollectionDepositPreference(boolean preferBank) {
        playerInventory.setCollectionDepositToBankPreferred(preferBank);
        if (geData != null) {
            geData.setCollectionDepositPreference(playerAuth, preferBank);
        }
        syncManager.sendCollectionSync();
        syncManager.sendFeedback(GEFeedbackChannel.COLLECTION, GEFeedbackLevel.INFO,
            preferBank
                ? "Collection entries now default to the bank."
                : "Collection entries now default to your inventory.");
    }

    /**
     * Toggle auto-bank setting.
     */
    public void handleToggleAutoBank(boolean enabled) {
        playerInventory.setAutoSendToBank(enabled);
        ModLogger.debug("Auto-bank toggled: %s", enabled);
        syncManager.sendCollectionSync();
    }

    /**
     * Toggle partial sale notifications.
     */
    public void handleToggleNotifyPartial(boolean enabled) {
        playerInventory.setNotifyPartialSales(enabled);
        ModLogger.debug("Notify partial sales toggled: %s", enabled);
        syncManager.sendCollectionSync();
    }

    /**
     * Toggle play sound on sale.
     */
    public void handleTogglePlaySound(boolean enabled) {
        playerInventory.setPlaySoundOnSale(enabled);
        ModLogger.debug("Play sound on sale toggled: %s", enabled);
        syncManager.sendCollectionSync();
    }

    // ===== INTERNAL HELPER =====

    /**
     * Collect a single entry by global index.
     * 
     * @param globalIndex The global index in the full collection list
     * @param sendToBank If true, deposit to bank; if false, deposit to inventory
     * @return true if successfully collected, false otherwise
     */
    private boolean collectSingleEntry(int globalIndex, boolean sendToBank) {
        if (globalIndex < 0 || geData == null) {
            return false;
        }
        
        CollectionItem removed = geData.collectFromCollectionBox(playerAuth, globalIndex);
        if (removed == null) {
            ModLogger.warn("Failed to remove collection index %d for player auth=%d", globalIndex, playerAuth);
            return false;
        }

        InventoryItem invItem = new InventoryItem(removed.getItemStringID(), removed.getQuantity());
        if (invItem.item == null) {
            playerInventory.insertIntoCollectionBox(globalIndex, removed);
            ModLogger.warn("Unknown item '%s' at index %d; restored", removed.getItemStringID(), globalIndex);
            return false;
        }

        if (serverClient == null) {
            playerInventory.insertIntoCollectionBox(globalIndex, removed);
            ModLogger.warn("No server client available for collectSingleEntry (auth=%d)", playerAuth);
            return false;
        }

        Level playerLevel = serverClient.getLevel();

        // Try bank first if preferred
        if (sendToBank && bankingService != null && bank != null) {
            boolean deposited = bank.getInventory().addItem(
                playerLevel,
                serverClient.playerMob,
                invItem,
                "grandexchange_collect",
                null
            );
            if (deposited) {
                ModLogger.info("Collected collection index %d to bank for auth=%d: %s x%d",
                    globalIndex, playerAuth, removed.getItemStringID(), removed.getQuantity());
                return true;
            }
            ModLogger.warn("Failed to deposit %s x%d to bank for auth=%d; falling back to inventory",
                removed.getItemStringID(), removed.getQuantity(), playerAuth);
        }

        // Fall back to player inventory
        boolean added = serverClient.playerMob.getInv().main.addItem(
            playerLevel,
            serverClient.playerMob,
            invItem,
            "grandexchange_collect",
            null
        );
        if (added) {
            ModLogger.info("Collected collection index %d to inventory for auth=%d: %s x%d",
                globalIndex, playerAuth, removed.getItemStringID(), removed.getQuantity());
            return true;
        }

        // Restore if failed
        playerInventory.insertIntoCollectionBox(globalIndex, removed);
        ModLogger.warn("Inventory full for auth=%d while collecting %s x%d; item restored",
            playerAuth, removed.getItemStringID(), removed.getQuantity());
        return false;
    }
}
